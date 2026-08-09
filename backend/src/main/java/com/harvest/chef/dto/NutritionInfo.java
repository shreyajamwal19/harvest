package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class NutritionInfo {
    private String queryTerm;
    private String matchedFoodName;
    private Double calories;
    private Double proteinGrams;
    private Double carbsGrams;
    private Double fatGrams;
    // Phase 7 (Part 2) - additive micronutrient fields, USDA-grounded like everything else on
    // this DTO. Any of these may be null when USDA's response simply didn't include that
    // nutrient for the matched food - never filled with a guessed value.
    private Double fiberGrams;
    private Double sugarGrams;
    private Double sodiumMg;
    private Double ironMg;
    private Double calciumMg;
    private Double potassiumMg;
    private Double vitaminAMcg;
    private Double vitaminCMg;
    private Double vitaminDMcg;
    private String servingSize;
    /** Always populated - nutrition figures must be traceable to a source, never invented. */
    private String source;
}
