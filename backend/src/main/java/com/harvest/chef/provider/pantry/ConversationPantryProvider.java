package com.harvest.chef.provider.pantry;

import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.PantryKnowledgeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Phase 2/3 implementation: pantry awareness is whatever the Retrieval
 * Orchestrator's planning step extracted from the current message. No
 * persistence yet - deliberately scoped out, same as Phase 1's memory.
 * A structured, persisted pantry (with expiry tracking) is a natural
 * future upgrade behind this same interface.
 */
@Component
public class ConversationPantryProvider implements PantryKnowledgeProvider {

    @Override
    public ProviderResult<List<String>> retrieve(List<String> mentionedIngredients) {
        long start = System.currentTimeMillis();
        List<String> items = mentionedIngredients == null
                ? List.of()
                : mentionedIngredients.stream().filter(Objects::nonNull).toList();

        return ProviderResult.<List<String>>builder()
                .data(items)
                .success(true)
                .providerName(getName())
                .confidence(items.isEmpty() ? 0.0 : 1.0)
                .completeness(1.0)
                .latencyMs(System.currentTimeMillis() - start)
                .reliability(getReliability())
                .retrievedAt(Instant.now())
                .build();
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.PANTRY;
    }

    @Override
    public String getName() {
        return "conversation-pantry";
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
        return 1.0;
    }
}
