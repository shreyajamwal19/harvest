package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.EvaluatedRecipe;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.retrieval.RecipeScoringEngine.RecipeScore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Orchestrates recipe evaluation: filters out already-shown/invalid
 * candidates, scores every survivor via {@link RecipeScoringEngine},
 * sorts deterministically, then diversifies the top results so a page
 * isn't five near-identical recipes back to back.
 *
 * All the actual scoring math (ingredient overlap, pantry utilization,
 * tiered ingredient importance, title relevance, exact-vs-synonym
 * weighting, multi-ingredient coverage, intent/dietary/budget/occasion
 * alignment, completeness, popularity heuristics, reliability) lives in
 * {@link RecipeScoringEngine} - kept separate so each ranking signal is
 * independently readable/extensible rather than one large method here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeEvaluationService {

    private static final int MAX_RESULTS = 3;

    private final RecipeScoringEngine scoringEngine;

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

        List<RecipeScore> scored = new ArrayList<>();
        for (RecipeCandidate candidate : candidates) {
            if (candidate == null || candidate.getTitle() == null || candidate.getTitle().isBlank()) {
                continue;
            }
            if (excluded.contains(normalizeTitle(candidate.getTitle()))) {
                continue;
            }
            scored.add(scoringEngine.score(candidate, plan));
        }

        scored.sort(Comparator
                .comparingDouble(RecipeScore::total).reversed()
                .thenComparing(rs -> rs.candidate().getTitle().toLowerCase(Locale.ROOT)));

        List<RecipeScore> topResults = scoringEngine.selectDiverseTopResults(scored, MAX_RESULTS);

        List<EvaluatedRecipe> results = new ArrayList<>();
        for (RecipeScore rs : topResults) {
            results.add(EvaluatedRecipe.builder()
                    .candidate(rs.candidate())
                    .rationale(buildRationale(rs, mentioned))
                    .missingIngredients(rs.missingIngredients())
                    .build());
        }

        log.info("[recipe-evaluation] {} candidates ({} excluded as already shown), {} scored, {} kept, top score={}",
                candidates.size(), excluded.size(), scored.size(), results.size(),
                scored.isEmpty() ? "n/a" : scored.get(0).total());
        return results;
    }

    private String buildRationale(RecipeScore score, List<String> mentioned) {
        if (mentioned.isEmpty()) {
            return "Best available match for your request from the " + score.candidate().getSource() + " catalog.";
        }
        if (score.explanations().isEmpty()) {
            return "A reasonable match for your request.";
        }
        return String.join(" ", score.explanations());
    }

    private String normalizeTitle(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }
}
