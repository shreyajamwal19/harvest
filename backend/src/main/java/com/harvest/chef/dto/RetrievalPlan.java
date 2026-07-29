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
    private String searchQuery;
    private String reasoningNote;
}
