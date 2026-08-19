package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Setters are required (not just Getter/Builder, unlike most other DTOs here) because
 * SessionStateService/ContextAssemblyService JSON-serialize and deserialize this class via the
 * default Spring Boot ObjectMapper to persist/restore the last shown recipe(s) for AI Chef
 * Reasoning Layer follow-up grounding - without setters, Jackson has no way to populate a
 * no-args-constructed instance's private fields.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeResponse {
    private String title;
    private String description;
    private Integer servings;
    private List<String> ingredients;
    private List<String> steps;
    private String notes;
    /** Why the Chef Brain selected this recipe specifically - always populated. */
    private String rationale;
    /** Ingredients this recipe needs that the user hasn't mentioned having. */
    private List<String> missingIngredients;
    /** "local", "themealdb", "generated", etc. */
    private String source;
    /** Real photo URL from the source provider, when it has one. Null otherwise - never faked. */
    private String imageUrl;
}
