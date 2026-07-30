package com.harvest.chef.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RequestIntent;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The Retrieval Orchestrator's planning step. Runs once, only when the
 * Sufficiency Gate has already returned SUFFICIENT. Decides:
 * - is this a recipe request or a technique question
 * - what ingredients/pantry items were mentioned
 * - whether external recipe sources are worth querying
 * - whether nutrition grounding is worth querying
 * Never decides what tools already excluded from scope (shopping, meal
 * planning, multi-day planning) would need - those aren't implemented.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalPlanningService {

    private static final String SYSTEM_PROMPT = """
            You are the Retrieval Orchestrator's planning stage inside Harvest's Chef Brain.
            The Goal Reasoning stage has already confirmed there is enough information to act on.
            Your job is to decide HOW to help, before anything is retrieved or generated.

            Decide:
            - intent: "RECIPE" if the user wants something to cook, "TECHNIQUE" if they're asking
              about a cooking method, a mistake, or food science (e.g. "my sauce split", "why is my
              bread dense") - these should NEVER be treated as recipe requests.
            - mentionedIngredients: ingredients or pantry items explicitly mentioned, exactly as named.
            - needsExternalRecipes: true only if the request is specific enough (a named cuisine,
              a named dish, an unusual combination) that a small local recipe set likely won't cover it.
            - needsNutritionGrounding: true only if the user cares about calories, protein, macros,
              or a health-driven constraint (diabetic-safe, high-protein, etc).
            - needsIngredientIntelligence: true if the request is fundamentally about an ingredient
              itself - substitutions, pairings, storage, shelf life - rather than a full recipe or
              a technique/mistake question.
            - searchQuery: a short natural-language query (3-8 words) to search recipe sources with.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "intent": "RECIPE" | "TECHNIQUE",
              "mentionedIngredients": ["..."],
              "needsExternalRecipes": boolean,
              "needsNutritionGrounding": boolean,
              "needsIngredientIntelligence": boolean,
              "searchQuery": "short search query",
              "reasoningNote": "one short sentence explaining the plan"
            }
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public RetrievalPlan plan(ConversationContext context, GoalAssessment assessment) {
        String userPrompt = "User's latest message: " + context.getCurrentMessage()
                + "\nInterpreted goal: " + assessment.getInterpretedGoal();

        String raw = anthropicClient.send(SYSTEM_PROMPT, userPrompt, 400);
        return parse(raw);
    }

    private RetrievalPlan parse(String raw) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);

            List<String> ingredients = new ArrayList<>();
            node.path("mentionedIngredients").forEach(item -> ingredients.add(item.asText()));

            return RetrievalPlan.builder()
                    .intent(RequestIntent.valueOf(node.path("intent").asText("RECIPE")))
                    .mentionedIngredients(ingredients)
                    .needsExternalRecipes(node.path("needsExternalRecipes").asBoolean(false))
                    .needsNutritionGrounding(node.path("needsNutritionGrounding").asBoolean(false))
                    .needsIngredientIntelligence(node.path("needsIngredientIntelligence").asBoolean(false))
                    .searchQuery(node.path("searchQuery").asText(""))
                    .reasoningNote(node.path("reasoningNote").asText(""))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse retrieval plan JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning stage returned an unexpected planning format");
        }
    }
}
