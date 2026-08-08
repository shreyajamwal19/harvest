package com.harvest.chef.planning.dto;

import com.harvest.chef.dto.RecipeResponse;
import lombok.Builder;
import lombok.Getter;

/** One deterministically-chosen recipe for one day of a generated meal plan. */
@Getter
@Builder
public class MealPlanDay {
    private String dayLabel;
    private RecipeResponse recipe;
}
