package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.retrieval.RetrievalPlanningService;
import com.harvest.chef.service.composer.RecipeComposer;
import com.harvest.chef.service.composer.TechniqueAnswerComposer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Stage 3 - Composition.
 *
 * Runs the Retrieval Orchestrator's planning step unconditionally right
 * after Context Assembly - there is no Goal Reasoning / Sufficiency Gate
 * stage upstream anymore - then dispatches to the matching composer based
 * on the plan's classified intent.
 */
@Service
@RequiredArgsConstructor
public class CompositionService {

    private final RetrievalPlanningService retrievalPlanningService;
    private final RecipeComposer recipeComposer;
    private final TechniqueAnswerComposer techniqueAnswerComposer;

    public ChefResponse compose(ConversationContext context) {
        RetrievalPlan plan = retrievalPlanningService.plan(context);

        return switch (plan.getIntent()) {
            case TECHNIQUE -> techniqueAnswerComposer.compose(context, plan);
            case RECIPE -> recipeComposer.compose(context, plan);
        };
    }
}
