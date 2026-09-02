package com.harvest.chef.personalization.dto;

import com.harvest.chef.personalization.entity.RecipeHistoryEntry;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class RecipeHistoryEntryResponse {
    private Long id;
    private String recipeTitle;
    private Instant cookedAt;

    public static RecipeHistoryEntryResponse from(RecipeHistoryEntry entry) {
        return RecipeHistoryEntryResponse.builder()
                .id(entry.getId())
                .recipeTitle(entry.getRecipeTitle())
                .cookedAt(entry.getCreatedAt())
                .build();
    }
}
