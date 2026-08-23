package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.EvaluatedRecipe;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalBundle;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.personalization.service.CookingHistoryService;
import com.harvest.chef.reasoning.ChefReasoningResult;
import com.harvest.chef.reasoning.ChefReasoningService;
import com.harvest.chef.retrieval.RecipeEvaluationService;
import com.harvest.chef.retrieval.RecipeGenerationService;
import com.harvest.chef.retrieval.RetrievalOrchestrator;
import com.harvest.chef.service.SessionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Used when the Retrieval Orchestrator has classified the request as
 * RECIPE. Retrieves grounded candidates, ranks them deterministically, and
 * falls back to an honest "nothing suitable" result (never a fabricated
 * recipe) when nothing grounded is a fit. The deterministic ranking and
 * recipe content are never influenced by the AI Chef Reasoning Layer - it
 * only ever supplies the conversational message describing what's already
 * been decided.
 *
 * On a "more" continuation turn (see {@link RetrievalPlan#isContinuation()}),
 * excludes recipes already shown earlier in the session instead of
 * re-running a brand-new, unrelated search. After composing, records the
 * search, shown titles, and full shown-recipe content via
 * {@link SessionStateService} so a later "more" turn (or a follow-up turn
 * handled directly in {@code CompositionService}) can continue from here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeComposer implements ResponseComposer {

    private final RetrievalOrchestrator retrievalOrchestrator;
    private final RecipeEvaluationService recipeEvaluationService;
    private final RecipeGenerationService recipeGenerationService;
    private final SessionStateService sessionStateService;
    private final ChefReasoningService chefReasoningService;
    private final CookingHistoryService cookingHistoryService;

    @Override
    public ChefResponse compose(ConversationContext context, RetrievalPlan plan) {
        RetrievalBundle bundle = retrievalOrchestrator.retrieve(context, plan);

        Set<String> excludedTitles = plan.isContinuation()
                ? context.getShownRecipeTitles()
                : Set.of();

        List<EvaluatedRecipe> evaluated =
                recipeEvaluationService.evaluate(context, plan, bundle.getRecipeCandidates(), excludedTitles);

        List<RecipeResponse> recipes;
        if (!evaluated.isEmpty()) {
            recipes = evaluated.stream().map(this::toRecipeResponse).toList();
        } else {
            List<RecipeCandidate> inspiration = bundle.getRecipeCandidates();
            recipes = recipeGenerationService.generate(inspiration);
        }

        // AI Chef Reasoning Layer: interprets the request and explains/recommends among the
        // recipes above. It cannot change which recipes are returned - only the message, and
        // only the response type when nothing was found at all (see ChefReasoningService).
        Optional<ChefReasoningResult> reasoning = chefReasoningService.reasonAboutRecipes(context, plan, recipes,
                bundle.getUserMemoryNotes(), bundle.getNutritionInfo(), bundle.getIngredientProfiles());

        String message = reasoning.map(ChefReasoningResult::getMessage)
                .orElseGet(() -> buildSummaryMessage(recipes, plan.isContinuation()));
        ChefResponseType responseType = reasoning.map(ChefReasoningResult::getType)
                .orElse(ChefResponseType.RECIPE);

        // Only persist "what was actually shown" when the response genuinely IS a recipe list -
        // if the AI Chef Reasoning Layer downgraded this to CLARIFYING_QUESTION, the user never
        // actually saw these recipes (ChefResponse.recipes is null below in that case too), so
        // recording them as shown would let a later "the second one" wrongly resolve against
        // recipes that were never displayed - a real conversational-integrity bug, not just a
        // cosmetic one.
        List<RecipeResponse> shownToUser = responseType == ChefResponseType.RECIPE ? recipes : List.of();
        sessionStateService.updateAfterRecipeTurn(context.getSessionId(), plan, shownToUser);

        if (responseType == ChefResponseType.RECIPE) {
            // Phase 6A - feeds Smart Variety on future turns. Never blocks or fails this
            // response; CookingHistoryService swallows its own errors.
            cookingHistoryService.recordShown(context.getUserId(), recipes);
        }

        return ChefResponse.builder()
                .type(responseType)
                .message(message)
                .recipes(responseType == ChefResponseType.RECIPE ? recipes : null)
                .build();
    }

    private RecipeResponse toRecipeResponse(EvaluatedRecipe evaluated) {
        RecipeCandidate candidate = evaluated.getCandidate();
        return RecipeResponse.builder()
                .title(candidate.getTitle())
                .description(candidate.getDescription())
                .servings(candidate.getServings())
                .ingredients(candidate.getIngredients())
                .steps(candidate.getSteps())
                .notes(null)
                .rationale(evaluated.getRationale())
                .missingIngredients(evaluated.getMissingIngredients())
                .source(candidate.getSource())
                .imageUrl(candidate.getImageUrl())
                .build();
    }

    private String buildSummaryMessage(List<RecipeResponse> recipes, boolean continuation) {
        if (recipes.isEmpty()) {
            return continuation
                    ? "That's everything I've got for that search - try a different ingredient or dish."
                    : "I couldn't find a suitable recipe for that from what's available right now.";
        }
        if (recipes.size() == 1) {
            return "Here's what I'd cook: " + recipes.get(0).getTitle() + ".";
        }
        List<String> titles = new ArrayList<>();
        recipes.forEach(r -> titles.add(r.getTitle()));
        String lead = continuation ? "A few more options worth cooking: " : "A few options worth cooking: ";
        return lead + String.join(", ", titles) + ".";
    }
}
