package com.harvest.chef.provider.external;

import com.harvest.chef.dto.RecipeCandidate;

import java.util.List;

/**
 * Implemented once per external recipe API. Spring collects every bean that
 * implements this interface into a list automatically (see
 * {@link ExternalRecipeProvider}), so adding a new API later means adding
 * one new class here - never touching the orchestration code.
 */
public interface ExternalRecipeApiClient {
    List<RecipeCandidate> search(String query);

    String apiName();
}
