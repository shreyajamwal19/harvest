package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The Retrieval Orchestrator's decision: what kind of request this is, and
 * which knowledge providers (if any) are worth querying for it.
 */
@Getter
@Builder
public class RetrievalPlan {
    private RequestIntent intent;
    private List<String> mentionedIngredients;
    private boolean needsExternalRecipes;
    private boolean needsNutritionGrounding;
    private boolean needsIngredientIntelligence;
    private String searchQuery;
    private String reasoningNote;
    /**
     * True when the current message is asking to continue a prior recipe
     * list ("more", "anything else", "another one", ...) rather than
     * starting a new search. When true, {@link #mentionedIngredients} and
     * {@link #searchQuery} may be reused from the session's last plan
     * instead of being freshly derived from this (often content-free)
     * message.
     */
    private boolean continuation;
    /**
     * Subset of {@link #mentionedIngredients} that reached their final form
     * via synonym resolution (e.g. "capsicum" -> "bell pepper") rather than
     * being typed exactly as-is. Used by recipe scoring to weight literal
     * matches slightly higher than synonym-derived ones. Empty on
     * continuation plans, since that distinction isn't persisted across
     * turns.
     */
    private List<String> synonymResolvedIngredients;
}
