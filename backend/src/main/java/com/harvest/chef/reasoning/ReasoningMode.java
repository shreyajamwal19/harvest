package com.harvest.chef.reasoning;

/**
 * Which kind of conversational reasoning a turn needs. Drives both prompt selection (see
 * {@code com.harvest.chef.reasoning.prompt}) and observability logging ("reasoning mode" in
 * {@code ChefReasoningService}). Conversation continuation and clarifying questions are
 * deliberately NOT separate modes here: continuation is a property every prompt builder gets
 * for free by including recent turns (see {@code RecipeContextFormatter}), and a clarifying
 * question is one possible response shape of {@link #RECIPE_EXPLANATION} (see its prompt's
 * response schema), not a distinct reasoning path.
 */
public enum ReasoningMode {
    /** Initial recipe turn, or a follow-up asking "why did you pick this?". */
    RECIPE_EXPLANATION,
    /** "Which one is better?", "what would you cook?", "which is healthier/cheaper/easier?". */
    RECIPE_COMPARISON,
    /** "Make it vegetarian", "double it", "use an air fryer", "no onions". */
    RECIPE_ADAPTATION,
    /** Serving suggestions, storage/freezing/reheating, technique tips, general chef chat about a shown recipe. */
    CHEF_COACHING,
    /** A standalone technique/knowledge question, not about a specific shown recipe. */
    TECHNIQUE_EXPLANATION
}
