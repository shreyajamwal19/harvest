package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.provider.technique.TechniqueKnowledgeProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Used when the Retrieval Orchestrator classifies the request as TECHNIQUE, not RECIPE. */
@Component
@RequiredArgsConstructor
public class TechniqueAnswerComposer implements ResponseComposer {

    private final TechniqueKnowledgeProvider techniqueKnowledgeProvider;

    @Override
    public ChefResponse compose(ConversationContext context, GoalAssessment assessment, RetrievalPlan plan) {
        String answer = techniqueKnowledgeProvider.answer(context.getCurrentMessage(), assessment.getInterpretedGoal());

        return ChefResponse.builder()
                .type(ChefResponseType.TECHNIQUE_ANSWER)
                .message(answer)
                .recipes(null)
                .build();
    }
}
