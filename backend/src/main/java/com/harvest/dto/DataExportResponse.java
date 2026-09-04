package com.harvest.dto;

import com.harvest.chef.pantry.dto.PantryItemResponse;
import com.harvest.chef.personalization.dto.RecipeHistoryEntryResponse;
import com.harvest.chef.personalization.dto.SavedRecipeResponse;
import com.harvest.chef.personalization.dto.UserPreferenceResponse;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * Everything Harvest stores about one user, bundled for download - the access-and-portability
 * counterpart to AccountDeletionService's erasure. Same tables that service deletes from,
 * reused here as a read instead of a write: pantry, saved recipes, cooking history,
 * preferences, plus the account record itself.
 */
@Getter
@Builder
public class DataExportResponse {
    private Long userId;
    private String name;
    private String email;
    private Instant accountCreatedAt;
    private Instant exportedAt;
    private List<PantryItemResponse> pantryItems;
    private List<SavedRecipeResponse> savedRecipes;
    private List<RecipeHistoryEntryResponse> cookingHistory;
    private List<UserPreferenceResponse> preferences;
}
