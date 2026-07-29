package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.GoalSufficiency;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.retrieval.RetrievalPlanningService;
import com.harvest.chef.service.composer.ClarifyingQuestionComposer;
import com.harvest.chef.service.composer.HonestNonAnswerComposer;
import com.harvest.chef.service.composer.RecipeComposer;
import com.harvest.chef.service.composer.TechniqueAnswerComposer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Stage 5 - Composition.
 *
 * For INSUFFICIENT/NON_ACTIONABLE, dispatches straight to the matching
 * composer. For SUFFICIENT, first runs the Retrieval Orchestrator's
 * planning step (Phase 2) to classify RECIPE vs TECHNIQUE and decide what
 * retrieval is worth doing, then dispatches accordingly.
 */
@Service
@RequiredArgsConstructor
public class CompositionService {

    private final RetrievalPlanningService retrievalPlanningService;
    private final RecipeComposer recipeComposer;
    private final TechniqueAnswerComposer techniqueAnswerComposer;
    private final ClarifyingQuestionComposer clarifyingQuestionComposer;
    private final HonestNonAnswerComposer honestNonAnswerComposer;

    public ChefResponse compose(ConversationContext context, GoalAssessment assessment, GoalSufficiency decision) {
        if (decision == GoalSufficiency.INSUFFICIENT) {
            return clarifyingQuestionComposer.compose(context, assessment, null);
        }
        if (decision == GoalSufficiency.NON_ACTIONABLE) {
            return honestNonAnswerComposer.compose(context, assessment, null);
        }

        RetrievalPlan plan = retrievalPlanningService.plan(context, assessment);

        return switch (plan.getIntent()) {
            case TECHNIQUE -> techniqueAnswerComposer.compose(context, assessment, plan);
            case RECIPE -> recipeComposer.compose(context, assessment, plan);
        };
    }
}
