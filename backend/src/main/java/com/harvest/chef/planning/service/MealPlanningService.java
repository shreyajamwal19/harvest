package com.harvest.chef.planning.service;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.knowledge.manager.KnowledgeProviderManager;
import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.personalization.service.CookingHistoryService;
import com.harvest.chef.planning.dto.MealPlanDay;
import com.harvest.chef.planning.dto.MealPlanResponse;
import com.harvest.chef.retrieval.RecipeCategoryClassifier;
import com.harvest.chef.retrieval.RecipeCategoryClassifier.Category;
import com.harvest.chef.retrieval.RecipeScoringEngine;
import com.harvest.chef.retrieval.RecipeScoringEngine.RecipeScore;
import com.harvest.chef.service.SessionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministically generates a 1/3/5/7-day meal plan. The engine - not the LLM - chooses every
 * recipe (LLM_RESPONSIBILITY: "The LLM does NOT choose meals. The deterministic engine does.").
 * Reuses {@link RecipeScoringEngine} for ranking (pantry-aware, personalized) and
 * {@link RecipeCategoryClassifier} for meal-type filtering and day-to-day variety - no separate
 * ranking logic is duplicated here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MealPlanningService {

    /** Candidate pool size pulled before ranking/selection - generous enough for real variety. */
    private static final int CANDIDATE_QUERY_LIMIT_INGREDIENTS = 6;

    private final KnowledgeProviderManager knowledgeProviderManager;
    private final RecipeScoringEngine scoringEngine;
    private final RecipeCategoryClassifier categoryClassifier;
    private final CookingHistoryService cookingHistoryService;
    private final SessionStateService sessionStateService;

    public MealPlanResponse generate(ConversationContext context, int days, Category mealTypeHint) {
        PantrySnapshot pantry = context.getPantry();

        String query = buildBrowseQuery(pantry);
        List<RecipeCandidate> pool = knowledgeProviderManager.retrieveRecipes(query, false);

        RetrievalPlan planForScoring = RetrievalPlan.builder()
                .mentionedIngredients(pantry.isEmpty() ? List.of() : pantry.ingredientNames())
                .preferenceTags(Set.of())
                .build();

        List<RecipeScore> scored = pool.stream()
                .filter(c -> c.getTitle() != null && !c.getTitle().isBlank())
                .map(c -> scoringEngine.score(c, planForScoring, context.getUserProfile(), pantry))
                .sorted(Comparator.comparingDouble(RecipeScore::total).reversed())
                .toList();

        List<MealPlanDay> selectedDays = selectDiversePlan(scored, days, mealTypeHint);

        recordAndPersist(context, selectedDays);

        return MealPlanResponse.builder().days(selectedDays).build();
    }

    /**
     * Swaps a single day: same deterministic scoring pass as {@link #generate}, but the caller
     * supplies every title already in the plan (every other day, not just neighbours) so the
     * replacement can never repeat one - lets the Meal Plan page offer a "Swap this day" action
     * per day instead of forcing a full regenerate. Returns null (never throws) if genuinely
     * nothing distinct is left in the pool - the caller treats that as "no alternative right now".
     */
    public MealPlanDay regenerateDay(ConversationContext context, List<String> excludeTitles, Category mealTypeHint) {
        PantrySnapshot pantry = context.getPantry();

        String query = buildBrowseQuery(pantry);
        List<RecipeCandidate> pool = knowledgeProviderManager.retrieveRecipes(query, false);

        RetrievalPlan planForScoring = RetrievalPlan.builder()
                .mentionedIngredients(pantry.isEmpty() ? List.of() : pantry.ingredientNames())
                .preferenceTags(Set.of())
                .build();

        List<RecipeScore> scored = pool.stream()
                .filter(c -> c.getTitle() != null && !c.getTitle().isBlank())
                .map(c -> scoringEngine.score(c, planForScoring, context.getUserProfile(), pantry))
                .sorted(Comparator.comparingDouble(RecipeScore::total).reversed())
                .toList();

        Set<String> usedTitles = new LinkedHashSet<>();
        if (excludeTitles != null) {
            excludeTitles.stream()
                    .filter(t -> t != null && !t.isBlank())
                    .forEach(t -> usedTitles.add(t.trim().toLowerCase(Locale.ROOT)));
        }

        List<MealPlanDay> onePick = new ArrayList<>();
        fillPlan(onePick, usedTitles, scored, 1, mealTypeHint, true, Optional.empty());
        if (onePick.isEmpty()) {
            // Relaxed pass mirrors generate()'s pass 2 - only if the strict pass (meal-type
            // hint honoured, non-meal categories excluded) couldn't find anything distinct left.
            fillPlan(onePick, usedTitles, scored, 1, null, false, Optional.empty());
        }
        if (onePick.isEmpty()) {
            return null;
        }

        MealPlanDay day = onePick.get(0);
        cookingHistoryService.recordShown(context.getUserId(), List.of(day.getRecipe()));
        log.info("[meal-plan] regenerated single day userId={}", context.getUserId());
        return day;
    }

    /**
     * Greedy variety-aware selection: walks the score-sorted pool once, skipping recipes whose
     * primary category repeats the immediately preceding day (MEAL_VARIETY: no four days of
     * chicken-anything in a row) and skipping non-meal categories (sauces/dips/desserts) unless
     * the pool is genuinely too small to fill every day without them - degrading gracefully
     * rather than returning fewer days than asked for.
     */
    private List<MealPlanDay> selectDiversePlan(List<RecipeScore> scored, int days, Category mealTypeHint) {
        List<MealPlanDay> plan = new ArrayList<>();
        Set<String> usedTitles = new LinkedHashSet<>();
        Optional<Category> lastCategory = Optional.empty();

        // Pass 1: strict - respects meal-type hint, avoids repeats and non-meal categories.
        fillPlan(plan, usedTitles, scored, days, mealTypeHint, true, lastCategory);
        // Pass 2: relaxed - only if pass 1 couldn't fill every day (small/pantry-constrained pool).
        if (plan.size() < days) {
            fillPlan(plan, usedTitles, scored, days, null, false, Optional.empty());
        }
        return plan;
    }

    private void fillPlan(List<MealPlanDay> plan, Set<String> usedTitles, List<RecipeScore> scored, int days,
                           Category mealTypeHint, boolean strict, Optional<Category> startingLastCategory) {
        Optional<Category> lastCategory = startingLastCategory;
        for (RecipeScore rs : scored) {
            if (plan.size() >= days) {
                break;
            }
            String normalizedTitle = rs.candidate().getTitle().trim().toLowerCase(Locale.ROOT);
            if (usedTitles.contains(normalizedTitle)) {
                continue;
            }
            Optional<Category> primary = categoryClassifier.primaryCategory(rs.candidate());

            if (strict) {
                if (primary.isPresent() && RecipeCategoryClassifier.NOT_A_MEAL.contains(primary.get())) {
                    continue;
                }
                if (mealTypeHint != null && primary.isPresent() && primary.get() != mealTypeHint) {
                    continue;
                }
                if (lastCategory.isPresent() && primary.isPresent() && lastCategory.get() == primary.get()) {
                    continue; // avoid back-to-back repeats of the same dish type
                }
            }

            usedTitles.add(normalizedTitle);
            plan.add(MealPlanDay.builder()
                    .dayLabel("Day " + (plan.size() + 1))
                    .recipe(toRecipeResponse(rs))
                    .build());
            lastCategory = primary;
        }
    }

    private RecipeResponse toRecipeResponse(RecipeScore score) {
        RecipeCandidate candidate = score.candidate();
        return RecipeResponse.builder()
                .title(candidate.getTitle())
                .description(candidate.getDescription())
                .servings(candidate.getServings())
                .ingredients(candidate.getIngredients())
                .steps(candidate.getSteps())
                .notes(null)
                .rationale(score.explanations().isEmpty()
                        ? "A solid pick for this day's meal plan." : String.join(" ", score.explanations()))
                .missingIngredients(score.missingIngredients())
                .source(candidate.getSource())
                .imageUrl(candidate.getImageUrl())
                .build();
    }

    /**
     * Biases the candidate pool toward what's already in the pantry (INGREDIENT_REUSE /
     * WASTE_REDUCTION) by querying on a handful of pantry ingredients rather than browsing
     * blind; falls back to an honest catalog browse when the pantry is empty.
     */
    private String buildBrowseQuery(PantrySnapshot pantry) {
        if (pantry.isEmpty()) {
            return "";
        }
        return String.join(" ", pantry.ingredientNames().stream()
                .limit(CANDIDATE_QUERY_LIMIT_INGREDIENTS).toList());
    }

    /**
     * Feeds the plan's recipes into Smart Variety (Phase 6A history) and session state, so a
     * follow-up "generate my grocery list" can shop for exactly this plan. Never fails the plan
     * itself - both downstream calls already swallow their own errors.
     */
    private void recordAndPersist(ConversationContext context, List<MealPlanDay> selectedDays) {
        List<RecipeResponse> recipes = selectedDays.stream().map(MealPlanDay::getRecipe).toList();
        if (recipes.isEmpty()) {
            return;
        }
        cookingHistoryService.recordShown(context.getUserId(), recipes);

        RetrievalPlan sessionPlan = RetrievalPlan.builder()
                .searchQuery("meal plan")
                .mentionedIngredients(context.getPantry().isEmpty() ? List.of() : context.getPantry().ingredientNames())
                .build();
        sessionStateService.updateAfterRecipeTurn(context.getSessionId(), sessionPlan, recipes);

        log.info("[meal-plan] generated userId={} days={}", context.getUserId(), recipes.size());
    }
}
