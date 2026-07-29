package com.harvest.chef.provider.pantry;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Phase 2 implementation: pantry awareness is whatever the Retrieval
 * Orchestrator's planning step extracted from the current message. No
 * persistence yet - deliberately scoped out, same as Phase 1's memory.
 */
@Component
public class ConversationPantryProvider implements PantryProvider {

    @Override
    public List<String> currentPantryItems(List<String> mentionedIngredients) {
        return mentionedIngredients == null
                ? List.of()
                : mentionedIngredients.stream().filter(Objects::nonNull).toList();
    }
}
