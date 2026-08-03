package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.reasoning.ChefReasoningResult;
import com.harvest.chef.reasoning.ChefReasoningService;
import com.harvest.chef.retrieval.FollowUpIntentDetector;
import com.harvest.chef.retrieval.RetrievalPlanningService;
import com.harvest.chef.service.composer.RecipeComposer;
import com.harvest.chef.service.composer.TechniqueAnswerComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Stage 3 - Composition.
 *
 * Checks for a follow-up turn about a recipe already shown this session
 * first ({@link FollowUpIntentDetector}) - if one is detected and the
 * session has previously shown recipes to ground against, it's handled
 * directly via the AI Chef Reasoning Layer with NO retrieval at all
 * (nothing to search or rank; the recipe(s) are already known).
 *
 * Otherwise, runs the Retrieval Orchestrator's planning step unconditionally
 * right after Context Assembly - there is no Goal Reasoning / Sufficiency
 * Gate stage upstream anymore - then dispatches to the matching composer
 * based on the plan's classified intent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompositionService {

    private final FollowUpIntentDetector followUpIntentDetector;
    private final ChefReasoningService chefReasoningService;
    private final RetrievalPlanningService retrievalPlanningService;
    private final RecipeComposer recipeComposer;
    private final TechniqueAnswerComposer techniqueAnswerComposer;

    public ChefResponse compose(ConversationContext context) {
        Optional<ChefResponse> followUp = tryComposeFollowUp(context);
        if (followUp.isPresent()) {
            return followUp.get();
        }

        RetrievalPlan plan = retrievalPlanningService.plan(context);

        return switch (plan.getIntent()) {
            case TECHNIQUE -> techniqueAnswerComposer.compose(context, plan);
            case RECIPE -> recipeComposer.compose(context, plan);
        };
    }

    /**
     * @return a composed response if this turn is a follow-up about previously shown recipe(s)
     *         AND the AI Chef Reasoning Layer is available to reason about it; empty otherwise,
     *         in which case the caller falls through to the normal retrieval/planning flow.
     */
    private Optional<ChefResponse> tryComposeFollowUp(ConversationContext context) {
        List<RecipeResponse> previouslyShown = context.getLastShownRecipes();
        if (previouslyShown == null || previouslyShown.isEmpty()) {
            return Optional.empty();
        }
        if (!followUpIntentDetector.isFollowUp(context.getCurrentMessage())) {
            return Optional.empty();
        }

        Optional<ChefReasoningResult> reasoning = chefReasoningService.reasonAboutFollowUp(context, previouslyShown);
        if (reasoning.isEmpty()) {
            // The reasoning layer is what makes a follow-up turn meaningful (adapting/comparing
            // the shown recipe(s) in prose) - without it, there's nothing useful to add beyond
            // re-running retrieval, so fall through to the normal flow rather than guess.
            log.info("[ai-chef] Follow-up detected but reasoning layer unavailable - "
                    + "falling back to normal retrieval flow.");
            return Optional.empty();
        }

        log.info("[ai-chef] Follow-up turn handled via AI Chef Reasoning Layer, no retrieval run.");
        return Optional.of(ChefResponse.builder()
                .type(ChefResponseType.RECIPE)
                .message(reasoning.get().getMessage())
                .recipes(previouslyShown)
                .build());
    }
}
