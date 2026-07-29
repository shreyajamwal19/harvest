package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
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
}
