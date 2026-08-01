package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.EvaluatedRecipe;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalBundle;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.retrieval.RecipeEvaluationService;
import com.harvest.chef.retrieval.RecipeGenerationService;
import com.harvest.chef.retrieval.RetrievalOrchestrator;
import com.harvest.chef.service.SessionStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Used when the Retrieval Orchestrator has classified the request as
 * RECIPE. Retrieves grounded candidates, ranks them deterministically, and
 * falls back to an honest "nothing suitable" result (never a fabricated
 * recipe) when nothing grounded is a fit.
 *
 * On a "more" continuation turn (see {@link RetrievalPlan#isContinuation()}),
 * excludes recipes already shown earlier in the session instead of
 * re-running a brand-new, unrelated search. After composing, records the
 * search and shown titles via {@link SessionStateService} so a later
 * "more" turn can continue from here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeComposer implements ResponseComposer {

    private final RetrievalOrchestrator retrievalOrchestrator;
    private final RecipeEvaluationService recipeEvaluationService;
    private final RecipeGenerationService recipeGenerationService;
    private final SessionStateService sessionStateService;

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

        String message = buildSummaryMessage(recipes, plan.isContinuation());

        sessionStateService.updateAfterRecipeTurn(context.getSessionId(), plan, recipes);

        return ChefResponse.builder()
                .type(ChefResponseType.RECIPE)
                .message(message)
                .recipes(recipes)
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
