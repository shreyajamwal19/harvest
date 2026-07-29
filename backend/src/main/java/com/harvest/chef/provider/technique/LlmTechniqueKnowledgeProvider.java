package com.harvest.chef.provider.technique;

import com.harvest.chef.client.AnthropicClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Grounds technique/food-science answers in general cooking knowledge via
 * the reasoning model itself, rather than recipe retrieval. A dedicated
 * knowledge-base source (a curated technique corpus) is a natural upgrade
 * path here without changing the provider's interface.
 */
@Component
@RequiredArgsConstructor
public class LlmTechniqueKnowledgeProvider implements TechniqueKnowledgeProvider {

    private static final String SYSTEM_PROMPT = """
            You are the Technique Knowledge provider inside Harvest's Chef Brain.
            Answer cooking-method, food-science, and kitchen-mistake questions directly and
            practically - no recipe, no ingredient list, no follow-up question.

            Explain what happened (if relevant), why it happened, and what to do about it -
            either to fix the current situation or to avoid it next time.

            Respond with ONLY the answer itself - three to five sentences, plain text, no JSON.
            """;

    private final AnthropicClient anthropicClient;

    @Override
    public String answer(String question, String interpretedGoal) {
        String userPrompt = "User's question: " + question + "\nInterpreted goal: " + interpretedGoal;
        return anthropicClient.send(SYSTEM_PROMPT, userPrompt, 350).trim();
    }
}
