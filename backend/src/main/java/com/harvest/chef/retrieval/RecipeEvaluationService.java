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

/**
 * Evaluates raw candidates from every recipe provider: ranks them by fit
 * and keeps at most 3.
 *
 * Deterministic, no LLM call. Scoring uses only metadata already present
 * on {@link RecipeCandidate} and {@link RetrievalPlan} - how many of the
 * user's mentioned ingredients a candidate actually uses, and the
 * candidate's source reliability (reusing the same reliability values
 * {@link com.harvest.chef.provider.recipe.LocalRecipeProvider} and
 * {@link com.harvest.chef.provider.external.ExternalRecipeProvider}
 * already report for themselves, rather than inventing new numbers).
 */
@Service
@Slf4j
public class RecipeEvaluationService {

    private static final int MAX_RESULTS = 3;

    // Mirrors the reliability values the providers themselves already declare
    // via KnowledgeProvider.getReliability() - kept in sync, not reinvented.
    private static final Map<String, Double> SOURCE_RELIABILITY = Map.of(
            "local", 0.95,
            "themealdb", 0.60,
            "generated", 0.50
    );

    public List<EvaluatedRecipe> evaluate(ConversationContext context, RetrievalPlan plan,
                                           List<RecipeCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<String> mentioned = plan.getMentionedIngredients() == null ? List.of() : plan.getMentionedIngredients();

        List<ScoredCandidate> scored = new ArrayList<>();
        for (RecipeCandidate candidate : candidates) {
            String ingredientsLower = String.join(" ", candidate.getIngredients() == null
                    ? List.of() : candidate.getIngredients()).toLowerCase(Locale.ROOT);

            int matchedCount = (int) mentioned.stream()
                    .filter(m -> ingredientsLower.contains(m.toLowerCase(Locale.ROOT)))
                    .count();

            List<String> missing = missingIngredients(candidate, mentioned);
            double score = score(candidate, mentioned, matchedCount);

            scored.add(new ScoredCandidate(candidate, score, matchedCount, missing));
        }

        scored.sort(Comparator.comparingDouble(ScoredCandidate::score).reversed());

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

        log.info("[recipe-evaluation] {} candidates in, {} kept, top score={}",
                candidates.size(), results.size(), scored.isEmpty() ? "n/a" : scored.get(0).score());
        return results;
    }

    /**
     * Ingredient overlap (primary factor) + source reliability (secondary
     * factor). When the user gave no raw ingredients (a named-dish search),
     * overlap contributes nothing and reliability alone orders candidates.
     */
    private double score(RecipeCandidate candidate, List<String> mentioned, int matchedCount) {
        String source = candidate.getSource() == null ? "" : candidate.getSource();
        double reliability = SOURCE_RELIABILITY.getOrDefault(source, 0.4);
        if (mentioned.isEmpty()) {
            return reliability;
        }
        double overlapFraction = (double) matchedCount / mentioned.size();
        return overlapFraction * 0.75 + reliability * 0.25;
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

    private record ScoredCandidate(RecipeCandidate candidate, double score, int matchedCount, List<String> missing) {
    }
}
