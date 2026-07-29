package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** Output of the Recipe Evaluation stage: a surviving candidate plus why it was chosen. */
@Getter
@Builder
@AllArgsConstructor
public class EvaluatedRecipe {
    private RecipeCandidate candidate;
    private String rationale;
    /** Ingredients the user doesn't appear to have, called out explicitly rather than hidden. */
    private java.util.List<String> missingIngredients;
}
