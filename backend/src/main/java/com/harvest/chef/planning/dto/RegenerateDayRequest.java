package com.harvest.chef.planning.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RegenerateDayRequest {

    /** Every recipe title already in the current plan, so the replacement can't repeat one. */
    private List<String> excludeTitles;

    /** "breakfast" | "lunch" | "dinner" | null for any - matches the plan's own meal-type filter. */
    private String mealType;
}
