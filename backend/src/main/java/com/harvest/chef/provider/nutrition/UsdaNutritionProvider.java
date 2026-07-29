package com.harvest.chef.provider.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvest.chef.client.SimpleHttpJsonClient;
import com.harvest.chef.dto.NutritionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
public class UsdaNutritionProvider implements NutritionProvider {

    private final SimpleHttpJsonClient httpJsonClient;
    private final NutritionProperties properties;

    @Override
    public List<NutritionInfo> lookup(List<String> ingredientNames) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.info("NUTRITION_API_KEY not configured - skipping nutrition grounding rather than guessing.");
            return List.of();
        }

        List<NutritionInfo> results = new ArrayList<>();
        for (String ingredient : ingredientNames) {
            lookupOne(ingredient).ifPresent(results::add);
        }
        return results;
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
