package com.harvest.chef.dto;

import com.harvest.chef.knowledge.model.IngredientProfile;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Aggregated results from every provider the Retrieval Orchestrator chose to query. */
@Getter
@Builder
public class RetrievalBundle {
    private List<RecipeCandidate> recipeCandidates;
    private List<String> userMemoryNotes;
    private List<NutritionInfo> nutritionInfo;
    private List<IngredientProfile> ingredientProfiles;
}
