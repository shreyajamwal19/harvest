package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.EvaluatedRecipe;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RetrievalPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates raw candidates from every recipe provider: scores every one of
 * them deterministically and keeps the strongest few.
 *
 * Deterministic, no LLM call. Scoring signals, each contributing a
 * weighted 0.0-1.0 component:
 * - ingredient overlap: how many of the user's mentioned ingredients this
 *   candidate actually uses
 * - pantry utilization: what share of the candidate's own ingredient list
 *   the user already has (how little shopping the recipe would require)
 * - missing-ingredient penalty: the inverse - candidates that need a lot
 *   the user didn't mention are penalized, not just ranked lower by
 *   omission
 * - completeness: whether the candidate has a real description, servings,
 *   and a non-trivial ingredient/step list (recipe rows imported with
 *   missing fields are real but weaker results)
 * - source reliability: the provider's own baseline trust score, reused
 *   rather than reinvented (see {@link com.harvest.chef.provider.recipe.LocalRecipeProvider}
 *   and {@link com.harvest.chef.provider.external.ExternalRecipeProvider})
 *
 * There is no popularity/rating signal because the imported dataset and
 * {@link RecipeCandidate} don't carry one - added honestly if that data
 * ever becomes available, not simulated in the meantime.
 *
 * Ties are broken deterministically by title so identical inputs always
 * produce the same ordering.
 */
@Service
@Slf4j
public class RecipeEvaluationService {

    private static final int MAX_RESULTS = 3;

    private static final double WEIGHT_OVERLAP = 0.35;
    private static final double WEIGHT_PANTRY_UTILIZATION = 0.20;
    private static final double WEIGHT_COMPLETENESS = 0.15;
    private static final double WEIGHT_SOURCE = 0.15;
    private static final double WEIGHT_MISSING_PENALTY = 0.15;

    // Mirrors the reliability values the providers themselves already declare
    // via KnowledgeProvider.getReliability() - kept in sync, not reinvented.
    private static final Map<String, Double> SOURCE_RELIABILITY = Map.of(
            "local", 0.95,
            "themealdb", 0.60,
            "generated", 0.50
    );

    public List<EvaluatedRecipe> evaluate(ConversationContext context, RetrievalPlan plan,
                                           List<RecipeCandidate> candidates) {
        return evaluate(context, plan, candidates, Set.of());
    }

    /**
     * @param excludedTitles normalized (trimmed, lowercased) titles to skip -
     *                        used on "more" turns so a continuation doesn't
     *                        repeat recipes already shown this session.
     */
    public List<EvaluatedRecipe> evaluate(ConversationContext context, RetrievalPlan plan,
                                           List<RecipeCandidate> candidates, Set<String> excludedTitles) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<String> mentioned = plan.getMentionedIngredients() == null ? List.of() : plan.getMentionedIngredients();
        Set<String> excluded = excludedTitles == null ? Set.of() : excludedTitles;

        List<ScoredCandidate> scored = new ArrayList<>();
        for (RecipeCandidate candidate : candidates) {
            if (candidate == null || candidate.getTitle() == null || candidate.getTitle().isBlank()) {
                continue;
            }
            if (excluded.contains(normalizeTitle(candidate.getTitle()))) {
                continue;
            }

            String ingredientsLower = String.join(" ", candidate.getIngredients() == null
                    ? List.of() : candidate.getIngredients()).toLowerCase(Locale.ROOT);

            int matchedCount = (int) mentioned.stream()
                    .filter(m -> ingredientsLower.contains(m.toLowerCase(Locale.ROOT)))
                    .count();

            List<String> missing = missingIngredients(candidate, mentioned);
            double score = score(candidate, mentioned, matchedCount, missing.size());

            scored.add(new ScoredCandidate(candidate, score, matchedCount, missing));
        }

        scored.sort(Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparing(sc -> sc.candidate().getTitle().toLowerCase(Locale.ROOT)));

        List<EvaluatedRecipe> results = new ArrayList<>();
        for (ScoredCandidate sc : scored) {
            if (results.size() >= MAX_RESULTS) {
                break;
            }
            results.add(EvaluatedRecipe.builder()
                    .candidate(sc.candidate())
                    .rationale(buildRationale(sc, mentioned))
                    .missingIngredients(sc.missing())
                    .build());
        }

        log.info("[recipe-evaluation] {} candidates in ({} excluded as already shown), {} kept, top score={}",
                candidates.size(), excluded.size(), results.size(),
                scored.isEmpty() ? "n/a" : scored.get(0).score());
        return results;
    }

    private double score(RecipeCandidate candidate, List<String> mentioned, int matchedCount, int missingCount) {
        List<String> ingredients = candidate.getIngredients() == null ? List.of() : candidate.getIngredients();

        String source = candidate.getSource() == null ? "" : candidate.getSource();
        double reliability = SOURCE_RELIABILITY.getOrDefault(source, 0.4);
        double completeness = completenessScore(candidate);

        if (mentioned.isEmpty()) {
            // No ingredient signal to score against (a category/browse request) -
            // fall back to completeness + reliability only, using the weight
            // the ingredient-driven signals would otherwise have claimed.
            double remainingWeight = WEIGHT_OVERLAP + WEIGHT_PANTRY_UTILIZATION + WEIGHT_MISSING_PENALTY + WEIGHT_SOURCE;
            return WEIGHT_COMPLETENESS * completeness + remainingWeight * reliability;
        }

        double overlapFraction = matchedCount / (double) mentioned.size();
        double pantryUtilization = ingredients.isEmpty() ? 0.0 : matchedCount / (double) ingredients.size();
        double missingPenalty = ingredients.isEmpty() ? 0.0 : missingCount / (double) ingredients.size();

        return WEIGHT_OVERLAP * overlapFraction
                + WEIGHT_PANTRY_UTILIZATION * pantryUtilization
                + WEIGHT_COMPLETENESS * completeness
                + WEIGHT_SOURCE * reliability
                - WEIGHT_MISSING_PENALTY * missingPenalty;
    }

    private double completenessScore(RecipeCandidate candidate) {
        int points = 0;
        if (candidate.getDescription() != null && !candidate.getDescription().isBlank()) {
            points++;
        }
        if (candidate.getServings() != null && candidate.getServings() > 0) {
            points++;
        }
        if (candidate.getSteps() != null && candidate.getSteps().size() >= 2) {
            points++;
        }
        if (candidate.getIngredients() != null && candidate.getIngredients().size() >= 2) {
            points++;
        }
        return points / 4.0;
    }

    private List<String> missingIngredients(RecipeCandidate candidate, List<String> mentioned) {
        List<String> ingredients = candidate.getIngredients() == null ? List.of() : candidate.getIngredients();
        List<String> missing = new ArrayList<>();
        for (String ingredientLine : ingredients) {
            String lower = ingredientLine.toLowerCase(Locale.ROOT);
            boolean userHasIt = mentioned.stream().anyMatch(m -> lower.contains(m.toLowerCase(Locale.ROOT)));
            if (!userHasIt) {
                missing.add(ingredientLine);
            }
        }
        return missing;
    }

    private String buildRationale(ScoredCandidate sc, List<String> mentioned) {
        if (mentioned.isEmpty()) {
            return "Best available match for your request from the " + sc.candidate().getSource() + " catalog.";
        }
        return "Uses " + sc.matchedCount() + " of the " + mentioned.size() + " ingredient(s) you mentioned.";
    }

    private String normalizeTitle(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }

    private record ScoredCandidate(RecipeCandidate candidate, double score, int matchedCount, List<String> missing) {
    }
}
