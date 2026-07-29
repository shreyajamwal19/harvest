package com.harvest.chef.dto;

/**
 * The allowed composition outputs. Phase 2 adds TECHNIQUE_ANSWER for
 * food-science/technique questions that need neither a recipe nor a
 * clarifying question - just a direct, grounded-as-possible answer.
 */
public enum ChefResponseType {
    RECIPE,
    TECHNIQUE_ANSWER,
    CLARIFYING_QUESTION,
    HONEST_NON_ANSWER
}
