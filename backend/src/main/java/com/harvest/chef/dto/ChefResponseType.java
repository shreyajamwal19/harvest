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
    HONEST_NON_ANSWER,
    /** Phase 6A - a deterministic memory-command turn (remember/forget/show/reset/...). */
    PROFILE_UPDATE,
    /** Phase 6B - a deterministic pantry-command turn (add/remove/consume/show/clear). */
    PANTRY_UPDATE,
    /** Phase 6B - a deterministically generated multi-day meal plan. */
    MEAL_PLAN,
    /** Phase 6B - a deterministically generated, pantry-subtracted grocery list. */
    SHOPPING_LIST
}
