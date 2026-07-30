package com.harvest.chef.provider.technique;

import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.CookingKnowledgeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Grounds technique/food-science answers in general cooking knowledge via
 * the reasoning model itself, rather than recipe retrieval. A dedicated
 * knowledge-base source (a curated technique corpus) is a natural upgrade
 * path here without changing the provider's interface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmTechniqueKnowledgeProvider implements CookingKnowledgeProvider {

    private static final String SYSTEM_PROMPT = """
            You are the Cooking Knowledge provider inside Harvest's Chef Brain.
            Answer cooking-method, food-science, and kitchen-mistake questions directly and
            practically - no recipe, no ingredient list, no follow-up question.

            Explain what happened (if relevant), why it happened, and what to do about it -
            either to fix the current situation or to avoid it next time.

            Respond with ONLY the answer itself - three to five sentences, plain text, no JSON.
            """;

    private final AnthropicClient anthropicClient;

    @Override
    public ProviderResult<String> retrieve(String question, String interpretedGoal) {
        long start = System.currentTimeMillis();
        try {
            String userPrompt = "User's question: " + question + "\nInterpreted goal: " + interpretedGoal;
            String answer = anthropicClient.send(SYSTEM_PROMPT, userPrompt, 350).trim();

            return ProviderResult.<String>builder()
                    .data(answer)
                    .success(true)
                    .providerName(getName())
                    .confidence(0.8)
                    .completeness(1.0)
                    .latencyMs(System.currentTimeMillis() - start)
                    .reliability(getReliability())
                    .retrievedAt(Instant.now())
                    .build();
        } catch (ChefReasoningException e) {
            log.warn("Cooking knowledge provider failed: {}", e.getMessage());
            return ProviderResult.failure(getName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.COOKING_KNOWLEDGE;
    }

    @Override
    public String getName() {
        return "llm-technique";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ProviderHealth healthStatus() {
        return ProviderHealth.UP;
    }

    @Override
    public double getReliability() {
        return 0.85;
    }
}
