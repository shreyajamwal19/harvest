package com.harvest.chef.provider.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvest.chef.client.SimpleHttpJsonClient;
import com.harvest.chef.dto.RecipeCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * TheMealDB (themealdb.com) - a free, keyless recipe API. One concrete
 * implementation of {@link ExternalRecipeApiClient}; more can be added
 * (Spoonacular, Edamam, ...) without changing anything else.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MealDbRecipeApiClient implements ExternalRecipeApiClient {

    private final SimpleHttpJsonClient httpJsonClient;
    private final MealDbProperties properties;

    @Override
    public List<RecipeCandidate> search(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }

        String url = properties.getBaseUrl() + "/search.php?s="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);

        Optional<JsonNode> response = httpJsonClient.getJson(url);
        if (response.isEmpty()) {
            return List.of();
        }

        JsonNode meals = response.get().path("meals");
        if (!meals.isArray()) {
            return List.of();
        }

        List<RecipeCandidate> candidates = new ArrayList<>();
        for (JsonNode meal : meals) {
            candidates.add(toCandidate(meal));
            if (candidates.size() >= 5) {
                break; // cap what a single provider contributes to a search
            }
        }
        return candidates;
    }

    @Override
    public String apiName() {
        return "themealdb";
    }

    private RecipeCandidate toCandidate(JsonNode meal) {
        List<String> ingredients = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String ingredient = meal.path("strIngredient" + i).asText("");
            String measure = meal.path("strMeasure" + i).asText("");
            if (ingredient.isBlank()) {
                continue;
            }
            String line = measure.isBlank() ? ingredient : (measure.trim() + " " + ingredient.trim());
            ingredients.add(line.trim());
        }

        List<String> steps = splitInstructions(meal.path("strInstructions").asText(""));

        String imageUrl = meal.path("strMealThumb").asText(null);
        if (imageUrl != null && imageUrl.isBlank()) {
            imageUrl = null;
        }

        return RecipeCandidate.builder()
                .title(meal.path("strMeal").asText("Untitled dish"))
                .description("A " + meal.path("strArea").asText("") + " " + meal.path("strCategory").asText("")
                        + " dish.")
                .servings(null)
                .ingredients(ingredients)
                .steps(steps)
                .source(apiName())
                .imageUrl(imageUrl)
                .build();
    }

    private List<String> splitInstructions(String rawInstructions) {
        List<String> steps = new ArrayList<>();
        for (String line : rawInstructions.split("\r\n|\n|\\. (?=[A-Z])")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                steps.add(trimmed.endsWith(".") ? trimmed : trimmed + ".");
            }
        }
        return steps;
    }
}
