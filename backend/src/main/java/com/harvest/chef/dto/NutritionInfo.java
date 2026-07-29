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
    /** Always populated - nutrition figures must be traceable to a source, never invented. */
    private String source;
}
