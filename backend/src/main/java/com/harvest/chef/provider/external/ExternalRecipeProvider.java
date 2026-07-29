package com.harvest.chef.provider.external;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.provider.recipe.RecipeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fans a search out to every {@link ExternalRecipeApiClient} Spring knows
 * about. Adding a new external recipe API is adding a new
 * ExternalRecipeApiClient bean - this class never changes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalRecipeProvider implements RecipeProvider {

    private final List<ExternalRecipeApiClient> apiClients;

    @Override
    public List<RecipeCandidate> search(String query) {
        List<RecipeCandidate> aggregated = new ArrayList<>();
        for (ExternalRecipeApiClient client : apiClients) {
            try {
                aggregated.addAll(client.search(query));
            } catch (Exception e) {
                // One external API failing should never break retrieval as a whole.
                log.warn("External recipe API '{}' failed: {}", client.apiName(), e.getMessage());
            }
        }
        return aggregated;
    }

    @Override
    public String providerName() {
        return "external";
    }
}
