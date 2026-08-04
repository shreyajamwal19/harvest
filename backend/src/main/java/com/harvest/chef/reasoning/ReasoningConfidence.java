package com.harvest.chef.reasoning;

/**
 * A deterministic, per-{@link ReasoningMode} confidence label (never model-estimated - that
 * would just be another thing to fabricate). Logged for observability and used to decide the
 * system prompt's instruction on when to prefer a clarifying question over a guess.
 */
public enum ReasoningConfidence {
    /** Talking directly about grounded, already-retrieved recipe data. */
    HIGH,
    /** Adapting a grounded recipe using ordinary cooking knowledge (still recipe-shaped). */
    MEDIUM,
    /** General cooking/technique advice not directly grounded in a specific retrieved recipe. */
    LOW
}
