package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.EvaluatedRecipe;
import com.harvest.chef.dto.GoalAssessment;
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
 * Used only when the Retrieval Orchestrator has classified the request as
 * RECIPE. Follows the priority chain: retrieve grounded candidates ->
 * evaluate/rerank them -> fall back to generation (informed by whatever
 * grounded candidates existed, even weak ones) only when evaluation keeps
 * nothing. Multiple recipes may come back, each with its own rationale.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeComposer implements ResponseComposer {

    private final RetrievalOrchestrator retrievalOrchestrator;
    private final RecipeEvaluationService recipeEvaluationService;
    private final RecipeGenerationService recipeGenerationService;

    @Override
    public ChefResponse compose(ConversationContext context, GoalAssessment assessment, RetrievalPlan plan) {
        RetrievalBundle bundle = retrievalOrchestrator.retrieve(context, plan);

        List<EvaluatedRecipe> evaluated =
                recipeEvaluationService.evaluate(context, assessment, plan, bundle.getRecipeCandidates());

        List<RecipeResponse> recipes;
        if (!evaluated.isEmpty()) {
            recipes = evaluated.stream().map(this::toRecipeResponse).toList();
        } else {
            // Nothing grounded was a strong enough fit - fall back to generation,
            // using any raw candidates as adaptation/combination inspiration.
            List<RecipeCandidate> inspiration = bundle.getRecipeCandidates();
            recipes = recipeGenerationService.generate(context, assessment, inspiration);
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
            return "I couldn't put together a solid recipe for that.";
        }
        if (recipes.size() == 1) {
            return "Here's what I'd cook: " + recipes.get(0).getTitle() + ".";
        }
        List<String> titles = new ArrayList<>();
        recipes.forEach(r -> titles.add(r.getTitle()));
        return "A few options worth cooking: " + String.join(", ", titles) + ".";
    }
}
