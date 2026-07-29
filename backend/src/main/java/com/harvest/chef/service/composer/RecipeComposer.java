package com.harvest.chef.service.composer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Used only when the Sufficiency Gate has already confirmed SUFFICIENT.
 * Produces one concrete, cookable recipe - no retrieval, no grounding tools
 * yet (those are later phases); Phase 1 reasons directly from the model's
 * own knowledge and the assembled context.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecipeComposer implements ResponseComposer {

    private static final String SYSTEM_PROMPT = """
            You are the Recipe Composition stage inside Harvest's Chef Brain.
            The Goal Reasoning stage has already determined the user has given enough \
            information to act on. Your ONLY job is to produce one concrete, cookable recipe \
            that fits the user's interpreted goal and stated context.

            Do not ask questions. Do not hedge. Produce a real, specific recipe.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "title": "recipe name",
              "description": "one or two sentence description, including why it fits what the user has",
              "servings": integer,
              "ingredients": ["quantity + ingredient", "..."],
              "steps": ["step 1", "step 2", "..."],
              "notes": "short optional tip, or null"
            }
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    @Override
    public ChefResponse compose(ConversationContext context, GoalAssessment assessment) {
        String userPrompt = "User's latest message: " + context.getCurrentMessage()
                + "\nInterpreted goal: " + assessment.getInterpretedGoal();

        String raw = anthropicClient.send(SYSTEM_PROMPT, userPrompt, 900);
        RecipeResponse recipe = parse(raw);

        return ChefResponse.builder()
                .type(ChefResponseType.RECIPE)
                .message(recipe.getDescription())
                .recipe(recipe)
                .build();
    }

    private RecipeResponse parse(String raw) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);

            List<String> ingredients = new ArrayList<>();
            node.path("ingredients").forEach(item -> ingredients.add(item.asText()));

            List<String> steps = new ArrayList<>();
            node.path("steps").forEach(item -> steps.add(item.asText()));

            JsonNode servingsNode = node.path("servings");

            return RecipeResponse.builder()
                    .title(node.path("title").asText(""))
                    .description(node.path("description").asText(""))
                    .servings(servingsNode.isMissingNode() || servingsNode.isNull() ? null : servingsNode.asInt())
                    .ingredients(ingredients)
                    .steps(steps)
                    .notes(node.path("notes").isNull() ? null : node.path("notes").asText(null))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse recipe JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning stage returned an unexpected recipe format");
        }
    }
}
