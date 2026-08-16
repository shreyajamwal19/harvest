package com.harvest.chef.personalization.dto;

import com.harvest.chef.dto.RecipeResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SavedRecipeResponse {
    private Long id;
    private Instant savedAt;
    private RecipeResponse recipe;
}
