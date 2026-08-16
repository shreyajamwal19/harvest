package com.harvest.chef.personalization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * What the frontend already has in memory for any recipe it has shown (from a chat turn or
 * meal plan) - posted back verbatim to save it. Deliberately a separate DTO from
 * {@code RecipeResponse}, which has no setters and isn't meant for inbound JSON binding.
 */
@Getter
@Setter
public class SaveRecipeRequest {

    @NotBlank(message = "Recipe title is required")
    private String title;

    private String description;
    private Integer servings;
    private List<String> ingredients;
    private List<String> steps;
    private String notes;
    private String rationale;
    private List<String> missingIngredients;
    private String source;
}
