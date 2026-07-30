package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.knowledge.manager.KnowledgeProviderManager;
import com.harvest.chef.knowledge.model.IngredientProfile;
import com.harvest.chef.exception.ChefReasoningException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Used when the Retrieval Orchestrator classifies the request as
 * TECHNIQUE, not RECIPE. When the plan flags ingredient intelligence as
 * relevant (e.g. "what can I substitute for buttermilk"), that context is
 * pulled in and folded into the answer rather than answered from the
 * model's own knowledge alone.
 */
@Component
@RequiredArgsConstructor
public class TechniqueAnswerComposer implements ResponseComposer {

    private final KnowledgeProviderManager knowledgeProviderManager;

    @Override
    public ChefResponse compose(ConversationContext context, GoalAssessment assessment, RetrievalPlan plan) {
        String question = context.getCurrentMessage();
        String goal = assessment.getInterpretedGoal();

        if (plan != null && plan.isNeedsIngredientIntelligence()
                && plan.getMentionedIngredients() != null && !plan.getMentionedIngredients().isEmpty()) {
            List<IngredientProfile> profiles =
                    knowledgeProviderManager.retrieveIngredientIntelligence(plan.getMentionedIngredients());
            question = appendIngredientContext(question, profiles);
        }

        String answer = knowledgeProviderManager.retrieveCookingKnowledge(question, goal);
        if (answer == null) {
            throw new ChefReasoningException("The Chef Brain couldn't produce a cooking knowledge answer right now.");
        }

        return ChefResponse.builder()
                .type(ChefResponseType.TECHNIQUE_ANSWER)
                .message(answer)
                .recipes(null)
                .build();
    }

    private String appendIngredientContext(String question, List<IngredientProfile> profiles) {
        if (profiles.isEmpty()) {
            return question;
        }
        StringBuilder enriched = new StringBuilder(question).append("\n\nKnown ingredient facts:\n");
        for (IngredientProfile profile : profiles) {
            enriched.append("- ").append(profile.getName()).append(": substitutes [")
                    .append(String.join(", ", profile.getSubstitutes() == null ? List.of() : profile.getSubstitutes()))
                    .append("], pairs with [")
                    .append(String.join(", ", profile.getFlavorPairings() == null ? List.of() : profile.getFlavorPairings()))
                    .append(']').append('\n');
        }
        return enriched.toString();
    }
}
