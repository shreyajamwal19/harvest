package com.harvest.chef.reasoning;

import com.harvest.chef.dto.ChefResponseType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Output of the AI Chef Reasoning Layer. Deliberately carries only a
 * conversational {@code message} (and, for the recipe-reasoning path, an
 * optional type override to {@link ChefResponseType#CLARIFYING_QUESTION}) -
 * never a recipe list. The set of recipes shown to the user is always the
 * deterministic pipeline's own ranked output; this layer only ever talks
 * about that data, it never chooses or fabricates what's in it.
 *
 * {@code mode} and {@code confidence} are carried through purely for
 * observability logging in {@code ChefReasoningService} - they don't change
 * how the response is rendered.
 */
@Getter
@Builder
@AllArgsConstructor
public class ChefReasoningResult {
    private ChefResponseType type;
    private String message;
    private ReasoningMode mode;
    private ReasoningConfidence confidence;
}
