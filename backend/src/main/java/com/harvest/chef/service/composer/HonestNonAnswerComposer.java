package com.harvest.chef.service.composer;

import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Used when the Sufficiency Gate returns NON_ACTIONABLE: no recipe or question would genuinely help. */
@Component
@RequiredArgsConstructor
public class HonestNonAnswerComposer implements ResponseComposer {

    private static final String SYSTEM_PROMPT = """
            You are the Honest Non-Answer stage inside Harvest's Chef Brain.
            This situation cannot be turned into a recipe or a useful follow-up question right now \
            (for example: the user has no ingredients at all, or the request isn't really about cooking).

            Be direct and honest instead of forcing a recipe. Briefly acknowledge the situation and, \
            only if genuinely useful, add one short sentence about what would help next.
            Never invent a recipe. Never pretend ingredients exist that weren't mentioned.
            Respond with ONLY the message itself - two to three sentences, plain text, no JSON.
            """;

    private final AnthropicClient anthropicClient;

    @Override
    public ChefResponse compose(ConversationContext context, GoalAssessment assessment) {
        String userPrompt = "User's latest message: " + context.getCurrentMessage()
                + "\nInterpreted goal: " + assessment.getInterpretedGoal();

        String message = anthropicClient.send(SYSTEM_PROMPT, userPrompt, 200).trim();

        return ChefResponse.builder()
                .type(ChefResponseType.HONEST_NON_ANSWER)
                .message(message)
                .recipe(null)
                .build();
    }
}
