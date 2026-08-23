package com.harvest.chef.knowledge.manager;

import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.provider.CookingKnowledgeProvider;
import com.harvest.chef.knowledge.provider.IngredientIntelligenceProvider;
import com.harvest.chef.knowledge.provider.KnowledgeProvider;
import com.harvest.chef.knowledge.provider.NutritionKnowledgeProvider;
import com.harvest.chef.knowledge.provider.RecipeKnowledgeProvider;
import com.harvest.chef.knowledge.provider.UserMemoryKnowledgeProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registration and discovery: Spring collects every bean implementing each
 * typed provider interface automatically (adding a provider is just adding
 * a bean - no registry code changes), and this class exposes what's
 * registered and its live health for observability.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeProviderRegistry {

    @Getter
    private final List<RecipeKnowledgeProvider> recipeProviders;
    @Getter
    private final List<NutritionKnowledgeProvider> nutritionProviders;
    @Getter
    private final List<IngredientIntelligenceProvider> ingredientIntelligenceProviders;
    @Getter
    private final List<CookingKnowledgeProvider> cookingKnowledgeProviders;
    @Getter
    private final List<UserMemoryKnowledgeProvider> userMemoryProviders;

    /** Every registered provider across every category, for logging/health reporting. */
    public List<KnowledgeProvider> allProviders() {
        return java.util.stream.Stream.of(
                        recipeProviders, nutritionProviders, ingredientIntelligenceProviders,
                        cookingKnowledgeProviders, userMemoryProviders)
                .flatMap(List::stream)
                .map(KnowledgeProvider.class::cast)
                .toList();
    }

    /** Health snapshot for every registered provider, keyed by category then provider name. */
    public Map<KnowledgeProviderType, Map<String, ProviderHealth>> healthSnapshot() {
        Map<KnowledgeProviderType, Map<String, ProviderHealth>> snapshot = new LinkedHashMap<>();
        for (KnowledgeProvider provider : allProviders()) {
            snapshot
                    .computeIfAbsent(provider.getType(), t -> new LinkedHashMap<>())
                    .put(provider.getName(), provider.healthStatus());
        }
        return snapshot;
    }
}
