package com.harvest.chef.dto;

/**
 * The only response shapes the Chef Brain is allowed to produce in Phase 1.
 * A recipe is one possible output, never the default one.
 */
public enum ChefResponseType {
    RECIPE,
    CLARIFYING_QUESTION,
    HONEST_NON_ANSWER
}
