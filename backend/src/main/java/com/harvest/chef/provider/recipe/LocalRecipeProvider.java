package com.harvest.chef.provider.recipe;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.entity.Recipe;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.RecipeKnowledgeProvider;
import com.harvest.chef.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Wraps the local Recipe table. One provider among several - the Chef
 * Brain's intelligence no longer depends on this table being large or
 * complete; it's just the fastest, most reliable source to check first,
 * and the one the Manager falls back from to external providers if it fails.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalRecipeProvider implements RecipeKnowledgeProvider {

    private final RecipeRepository recipeRepository;

    @Override
    public ProviderResult<List<RecipeCandidate>> retrieve(String query) {
        long start = System.currentTimeMillis();
        try {
            List<Recipe> matches = recipeRepository.searchByTerm(query);
            List<RecipeCandidate> candidates = matches.stream().map(this::toCandidate).toList();

            return ProviderResult.<List<RecipeCandidate>>builder()
                    .data(candidates)
                    .success(true)
                    .providerName(getName())
                    .confidence(candidates.isEmpty() ? 0.0 : 0.75)
                    .completeness(candidates.isEmpty() ? 0.0 : 1.0)
                    .latencyMs(System.currentTimeMillis() - start)
                    .reliability(getReliability())
                    .retrievedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.warn("Local recipe provider failed: {}", e.getMessage());
            return ProviderResult.failure(getName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.RECIPE;
    }

    @Override
    public String getName() {
        return "local";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ProviderHealth healthStatus() {
        try {
            recipeRepository.count();
            return ProviderHealth.UP;
        } catch (Exception e) {
            return ProviderHealth.DOWN;
        }
    }

    @Override
    public double getReliability() {
        return 0.95;
    }

    private RecipeCandidate toCandidate(Recipe recipe) {
        return RecipeCandidate.builder()
                .title(recipe.getTitle())
                .description(recipe.getDescription())
                .servings(recipe.getServings())
                .ingredients(recipe.getIngredients())
                .steps(recipe.getSteps())
                .source(getName())
                .build();
    }
}
