package com.harvest.chef.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The final fallback in the priority chain: grounded recipe -> adapt a
 * grounded recipe -> combine grounded ideas -> generate. If weak candidates
 * exist (rejected by evaluation but not irrelevant), they're passed in as
 * inspiration so generation adapts/combines rather than inventing blind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeGenerationService {

    private static final String SYSTEM_PROMPT = """
            You are the Recipe Generation stage inside Harvest's Chef Brain - the last resort in the
            priority chain: grounded recipe, then adapting a grounded recipe, then combining grounded
            ideas, then generating from scratch only when nothing else fits.

            If candidate recipes are provided as inspiration, prefer adapting or combining them over
            inventing something unrelated. If none are provided, generate a genuinely good, specific,
            cookable recipe from your own knowledge.

            Never invent ingredients the user doesn't have without clearly listing them as missing.
            Produce 1 to 2 recipes, no more.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "recipes": [
                {
                  "title": "recipe name",
                  "description": "one or two sentence description",
                  "servings": integer,
                  "ingredients": ["quantity + ingredient", "..."],
                  "steps": ["step 1", "step 2", "..."],
                  "notes": "short optional tip, or null",
                  "rationale": "one sentence on why this recipe, and whether it was adapted, combined, or generated",
                  "missingIngredients": ["ingredients not mentioned as available, or empty list"]
                }
              ]
            }
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public List<RecipeResponse> generate(ConversationContext context, GoalAssessment assessment,
                                          List<RecipeCandidate> inspiration) {
        String raw = anthropicClient.send(SYSTEM_PROMPT, buildUserPrompt(context, assessment, inspiration), 1200);
        return parse(raw);
    }

    private String buildUserPrompt(ConversationContext context, GoalAssessment assessment,
                                    List<RecipeCandidate> inspiration) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User's latest message: ").append(context.getCurrentMessage()).append('\n');
        prompt.append("Interpreted goal: ").append(assessment.getInterpretedGoal()).append('\n');

        if (inspiration != null && !inspiration.isEmpty()) {
            prompt.append("\nInspiration candidates (adapt or combine these if they help):\n");
            for (RecipeCandidate candidate : inspiration) {
                prompt.append("- ").append(candidate.getTitle()).append(": ")
                        .append(String.join(", ", candidate.getIngredients() == null ? List.of() : candidate.getIngredients()))
                        .append('\n');
            }
        } else {
            prompt.append("\nNo grounded candidates were a good fit - generate from scratch.\n");
        }

        return prompt.toString();
    }

    private List<RecipeResponse> parse(String raw) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            JsonNode recipesNode = node.path("recipes");

            List<RecipeResponse> results = new ArrayList<>();
            for (JsonNode recipeNode : recipesNode) {
                List<String> ingredients = new ArrayList<>();
                recipeNode.path("ingredients").forEach(item -> ingredients.add(item.asText()));

                List<String> steps = new ArrayList<>();
                recipeNode.path("steps").forEach(item -> steps.add(item.asText()));

                List<String> missing = new ArrayList<>();
                recipeNode.path("missingIngredients").forEach(item -> missing.add(item.asText()));

                JsonNode servingsNode = recipeNode.path("servings");

                results.add(RecipeResponse.builder()
                        .title(recipeNode.path("title").asText(""))
                        .description(recipeNode.path("description").asText(""))
                        .servings(servingsNode.isMissingNode() || servingsNode.isNull() ? null : servingsNode.asInt())
                        .ingredients(ingredients)
                        .steps(steps)
                        .notes(recipeNode.path("notes").isNull() ? null : recipeNode.path("notes").asText(null))
                        .rationale(recipeNode.path("rationale").asText(""))
                        .missingIngredients(missing)
                        .source("generated")
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse generated recipe JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning stage returned an unexpected recipe format");
        }
    }
}
