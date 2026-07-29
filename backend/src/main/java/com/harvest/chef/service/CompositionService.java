package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.GoalSufficiency;
import com.harvest.chef.service.composer.ClarifyingQuestionComposer;
import com.harvest.chef.service.composer.HonestNonAnswerComposer;
import com.harvest.chef.service.composer.RecipeComposer;
import com.harvest.chef.service.composer.ResponseComposer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Stage 5 - Composition.
 *
 * Pure dispatch: routes to exactly one of the three allowed composer
 * strategies based on the Sufficiency Gate's decision. Holds no reasoning
 * or prompt logic itself - that lives in the individual composers.
 */
@Service
@RequiredArgsConstructor
public class CompositionService {

    private final RecipeComposer recipeComposer;
    private final ClarifyingQuestionComposer clarifyingQuestionComposer;
    private final HonestNonAnswerComposer honestNonAnswerComposer;

    public ChefResponse compose(ConversationContext context, GoalAssessment assessment, GoalSufficiency decision) {
        ResponseComposer composer = switch (decision) {
            case SUFFICIENT -> recipeComposer;
            case INSUFFICIENT -> clarifyingQuestionComposer;
            case NON_ACTIONABLE -> honestNonAnswerComposer;
        };
        return composer.compose(context, assessment);
    }
}
