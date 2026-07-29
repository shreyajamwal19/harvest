package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/** Raw output of any RecipeProvider - not yet ranked or filtered. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeCandidate {
    private String title;
    private String description;
    private Integer servings;
    private List<String> ingredients;
    private List<String> steps;
    /** e.g. "local", "themealdb" - which provider produced this candidate. */
    private String source;
}
