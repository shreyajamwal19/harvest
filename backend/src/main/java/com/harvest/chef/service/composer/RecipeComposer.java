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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Used when the Retrieval Orchestrator has classified the request as
 * RECIPE. Retrieves grounded candidates, ranks them deterministically, and
 * falls back to an honest "nothing suitable" result (never a fabricated
 * recipe) when nothing grounded is a fit.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeComposer implements ResponseComposer {

    private final RetrievalOrchestrator retrievalOrchestrator;
    private final RecipeEvaluationService recipeEvaluationService;
    private final RecipeGenerationService recipeGenerationService;

    @Override
    public ChefResponse compose(ConversationContext context, RetrievalPlan plan) {
        RetrievalBundle bundle = retrievalOrchestrator.retrieve(context, plan);

        List<EvaluatedRecipe> evaluated =
                recipeEvaluationService.evaluate(context, plan, bundle.getRecipeCandidates());

        List<RecipeResponse> recipes;
        if (!evaluated.isEmpty()) {
            recipes = evaluated.stream().map(this::toRecipeResponse).toList();
        } else {
            List<RecipeCandidate> inspiration = bundle.getRecipeCandidates();
            recipes = recipeGenerationService.generate(inspiration);
        }

        String message = buildSummaryMessage(recipes);

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

    private String buildSummaryMessage(List<RecipeResponse> recipes) {
        if (recipes.isEmpty()) {
            return "I couldn't find a suitable recipe for that from what's available right now.";
        }
        if (recipes.size() == 1) {
            return "Here's what I'd cook: " + recipes.get(0).getTitle() + ".";
        }
        List<String> titles = new ArrayList<>();
        recipes.forEach(r -> titles.add(r.getTitle()));
        return "A few options worth cooking: " + String.join(", ", titles) + ".";
    }
}
