package com.harvest.chef.provider.ingredient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.knowledge.model.IngredientProfile;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.IngredientIntelligenceProvider;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns each ingredient into a real entity - substitutes, flavor pairings,
 * storage, shelf life, seasonality, prep tips - rather than a bare string.
 * LLM-grounded for now; a curated ingredient database is a natural upgrade
 * path behind this same interface.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmIngredientIntelligenceProvider implements IngredientIntelligenceProvider {

    private static final String SYSTEM_PROMPT = """
            You are the Ingredient Intelligence provider inside Harvest's Chef Brain.
            For each ingredient given, provide practical, accurate information: what it can be
            substituted with, what it pairs well with, how to store it, roughly how long it keeps,
            when it's in season (or "year-round" if not seasonal), and one or two preparation tips.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "ingredients": [
                {
                  "name": "ingredient name",
                  "substitutes": ["..."],
                  "flavorPairings": ["..."],
                  "storageAdvice": "one sentence",
                  "shelfLife": "short phrase, e.g. '1 week refrigerated'",
                  "seasonality": "short phrase",
                  "preparationTips": ["..."]
                }
              ]
            }
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    @Override
    public ProviderResult<List<IngredientProfile>> retrieve(List<String> ingredientNames) {
        long start = System.currentTimeMillis();

        if (ingredientNames == null || ingredientNames.isEmpty()) {
            return ProviderResult.<List<IngredientProfile>>builder()
                    .data(List.of())
                    .success(true)
                    .providerName(getName())
                    .confidence(0.0)
                    .completeness(0.0)
                    .latencyMs(System.currentTimeMillis() - start)
                    .reliability(getReliability())
                    .retrievedAt(Instant.now())
                    .build();
        }

        try {
            String userPrompt = "Ingredients: " + String.join(", ", ingredientNames);
            String raw = anthropicClient.send(SYSTEM_PROMPT, userPrompt, 700);
            List<IngredientProfile> profiles = parse(raw);

            double completeness = (double) profiles.size() / ingredientNames.size();

            return ProviderResult.<List<IngredientProfile>>builder()
                    .data(profiles)
                    .success(true)
                    .providerName(getName())
                    .confidence(profiles.isEmpty() ? 0.0 : 0.75)
                    .completeness(completeness)
                    .latencyMs(System.currentTimeMillis() - start)
                    .reliability(getReliability())
                    .retrievedAt(Instant.now())
                    .build();
        } catch (ChefReasoningException e) {
            log.warn("Ingredient intelligence provider failed: {}", e.getMessage());
            return ProviderResult.failure(getName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.INGREDIENT_INTELLIGENCE;
    }

    @Override
    public String getName() {
        return "llm-ingredient-intelligence";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ProviderHealth healthStatus() {
        return ProviderHealth.UP;
    }

    @Override
    public double getReliability() {
        return 0.75;
    }

    private List<IngredientProfile> parse(String raw) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            JsonNode ingredientsNode = node.path("ingredients");

            List<IngredientProfile> profiles = new ArrayList<>();
            for (JsonNode ingredientNode : ingredientsNode) {
                List<String> substitutes = new ArrayList<>();
                ingredientNode.path("substitutes").forEach(item -> substitutes.add(item.asText()));

                List<String> pairings = new ArrayList<>();
                ingredientNode.path("flavorPairings").forEach(item -> pairings.add(item.asText()));

                List<String> prepTips = new ArrayList<>();
                ingredientNode.path("preparationTips").forEach(item -> prepTips.add(item.asText()));

                profiles.add(IngredientProfile.builder()
                        .name(ingredientNode.path("name").asText(""))
                        .substitutes(substitutes)
                        .flavorPairings(pairings)
                        .storageAdvice(ingredientNode.path("storageAdvice").asText(""))
                        .shelfLife(ingredientNode.path("shelfLife").asText(""))
                        .seasonality(ingredientNode.path("seasonality").asText(""))
                        .preparationTips(prepTips)
                        .source(getName())
                        .build());
            }
            return profiles;
        } catch (Exception e) {
            log.error("Failed to parse ingredient intelligence JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning stage returned an unexpected ingredient format");
        }
    }
}
