package com.harvest.chef.dto;

/**
 * Outcome of the Sufficiency Gate stage.
 *
 * SUFFICIENT      - enough context exists to reason toward a recipe.
 * INSUFFICIENT    - the goal is cooking-related but missing concrete information;
 *                    a clarifying question can realistically resolve it.
 * NON_ACTIONABLE  - no clarifying question would help right now (nothing to cook
 *                    with, or the request isn't really about cooking at all).
 */
public enum GoalSufficiency {
    SUFFICIENT,
    INSUFFICIENT,
    NON_ACTIONABLE
}
