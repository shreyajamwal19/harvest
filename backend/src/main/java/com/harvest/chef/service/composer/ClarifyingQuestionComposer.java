package com.harvest.chef.service.composer;

import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RetrievalPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Used when the Sufficiency Gate returns INSUFFICIENT: a real goal, missing concrete detail. */
@Component
@RequiredArgsConstructor
public class ClarifyingQuestionComposer implements ResponseComposer {

    private static final String SYSTEM_PROMPT = """
            You are the Clarifying Question stage inside Harvest's Chef Brain.
            The Goal Reasoning stage has determined there isn't yet enough information to help.
            Ask ONE short, specific, intelligent follow-up question that would let you actually help.
            Do not apologize. Do not explain your reasoning. Do not suggest a recipe.
            Respond with ONLY the question itself - one or two sentences, plain text, no JSON, no prefix.
            """;

    private final AnthropicClient anthropicClient;

    @Override
    public ChefResponse compose(ConversationContext context, GoalAssessment assessment, RetrievalPlan plan) {
        String userPrompt = "User's latest message: " + context.getCurrentMessage()
                + "\nInterpreted goal: " + assessment.getInterpretedGoal()
                + "\nWhat's missing: " + assessment.getMissingInformation();

        String question = anthropicClient.send(SYSTEM_PROMPT, userPrompt, 150).trim();

        return ChefResponse.builder()
                .type(ChefResponseType.CLARIFYING_QUESTION)
                .message(question)
                .recipes(null)
                .build();
    }
}
