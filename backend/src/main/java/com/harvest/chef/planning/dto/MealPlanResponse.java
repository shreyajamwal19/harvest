package com.harvest.chef.planning.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** The full deterministic output of {@code MealPlanningService} - never LLM-generated. */
@Getter
@Builder
public class MealPlanResponse {
    private List<MealPlanDay> days;
}
