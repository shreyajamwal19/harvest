package com.harvest.chef.provider.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvest.chef.client.SimpleHttpJsonClient;
import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.NutritionKnowledgeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * USDA FoodData Central. If no API key is configured, this provider
 * deliberately returns no results rather than guessing - nutrition figures
 * must always be traceable to a real source.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsdaNutritionProvider implements NutritionKnowledgeProvider {

    private final SimpleHttpJsonClient httpJsonClient;
    private final NutritionProperties properties;

    @Override
    public ProviderResult<List<NutritionInfo>> retrieve(List<String> ingredientNames) {
        long start = System.currentTimeMillis();

        if (!isAvailable()) {
            log.info("NUTRITION_API_KEY not configured - skipping nutrition grounding rather than guessing.");
            return ProviderResult.failure(getName(), "Nutrition API key not configured",
                    System.currentTimeMillis() - start);
        }

        List<NutritionInfo> results = new ArrayList<>();
        for (String ingredient : ingredientNames) {
            lookupOne(ingredient).ifPresent(results::add);
        }

        double completeness = ingredientNames.isEmpty() ? 0.0 : (double) results.size() / ingredientNames.size();

        return ProviderResult.<List<NutritionInfo>>builder()
                .data(results)
                .success(true)
                .providerName(getName())
                .confidence(results.isEmpty() ? 0.0 : 0.9)
                .completeness(completeness)
                .latencyMs(System.currentTimeMillis() - start)
                .reliability(getReliability())
                .retrievedAt(Instant.now())
                .build();
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.NUTRITION;
    }

    @Override
    public String getName() {
        return "usda-fdc";
    }

    @Override
    public boolean isAvailable() {
        return properties.getApiKey() != null && !properties.getApiKey().isBlank();
    }

    @Override
    public ProviderHealth healthStatus() {
        return isAvailable() ? ProviderHealth.UP : ProviderHealth.DOWN;
    }

    @Override
    public double getReliability() {
        return 0.9;
    }

    private Optional<NutritionInfo> lookupOne(String ingredientName) {
        String url = properties.getBaseUrl() + "/foods/search?query="
                + URLEncoder.encode(ingredientName, StandardCharsets.UTF_8)
                + "&pageSize=1&api_key=" + properties.getApiKey();

        Optional<JsonNode> response = httpJsonClient.getJson(url);
        if (response.isEmpty()) {
            return Optional.empty();
        }

        JsonNode foods = response.get().path("foods");
        if (!foods.isArray() || foods.isEmpty()) {
            return Optional.empty();
        }

        JsonNode food = foods.get(0);
        JsonNode nutrients = food.path("foodNutrients");

        Double calories = extractNutrient(nutrients, "Energy");
        Double protein = extractNutrient(nutrients, "Protein");
        Double carbs = extractNutrient(nutrients, "Carbohydrate, by difference");
        Double fat = extractNutrient(nutrients, "Total lipid (fat)");

        return Optional.of(NutritionInfo.builder()
                .queryTerm(ingredientName)
                .matchedFoodName(food.path("description").asText(ingredientName))
                .calories(calories)
                .proteinGrams(protein)
                .carbsGrams(carbs)
                .fatGrams(fat)
                .source("USDA FoodData Central")
                .build());
    }

    private Double extractNutrient(JsonNode nutrients, String nutrientName) {
        if (!nutrients.isArray()) {
            return null;
        }
        for (JsonNode nutrient : nutrients) {
            if (nutrientName.equalsIgnoreCase(nutrient.path("nutrientName").asText(""))) {
                return nutrient.path("value").isMissingNode() ? null : nutrient.path("value").asDouble();
            }
        }
        return null;
    }
}
