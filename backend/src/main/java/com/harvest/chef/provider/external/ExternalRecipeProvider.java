package com.harvest.chef.provider.external;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.RecipeKnowledgeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Fans a search out to every {@link ExternalRecipeApiClient} Spring knows
 * about (currently TheMealDB; Spoonacular/Edamam are architecture-ready -
 * add a new ExternalRecipeApiClient bean and this class never changes).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExternalRecipeProvider implements RecipeKnowledgeProvider {

    private final List<ExternalRecipeApiClient> apiClients;

    @Override
    public ProviderResult<List<RecipeCandidate>> retrieve(String query) {
        long start = System.currentTimeMillis();
        List<RecipeCandidate> aggregated = new ArrayList<>();
        int failedClients = 0;

        for (ExternalRecipeApiClient client : apiClients) {
            try {
                aggregated.addAll(client.search(query));
            } catch (Exception e) {
                // One external API failing should never break retrieval as a whole.
                failedClients++;
                log.warn("External recipe API '{}' failed: {}", client.apiName(), e.getMessage());
            }
        }

        boolean allFailed = !apiClients.isEmpty() && failedClients == apiClients.size();
        if (allFailed) {
            return ProviderResult.failure(getName(), "All external recipe APIs failed",
                    System.currentTimeMillis() - start);
        }

        return ProviderResult.<List<RecipeCandidate>>builder()
                .data(aggregated)
                .success(true)
                .providerName(getName())
                .confidence(aggregated.isEmpty() ? 0.0 : 0.6)
                .completeness(failedClients == 0 ? 1.0 : 0.5)
                .latencyMs(System.currentTimeMillis() - start)
                .reliability(getReliability())
                .retrievedAt(Instant.now())
                .build();
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.RECIPE;
    }

    @Override
    public String getName() {
        return "external";
    }

    @Override
    public boolean isAvailable() {
        return !apiClients.isEmpty();
    }

    @Override
    public ProviderHealth healthStatus() {
        return apiClients.isEmpty() ? ProviderHealth.DOWN : ProviderHealth.UP;
    }

    @Override
    public double getReliability() {
        return 0.6;
    }
}
