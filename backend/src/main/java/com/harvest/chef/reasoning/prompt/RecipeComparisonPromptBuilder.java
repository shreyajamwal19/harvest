package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the prompt for {@link com.harvest.chef.reasoning.ReasoningMode#RECIPE_COMPARISON}:
 * "which one is better?", "what would you cook?", "which is healthier/cheaper/easier/freezes
 * better?". Grounded only in the recipe(s) already shown this session - never a fresh search.
 */
@Component
public class RecipeComparisonPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an experienced chef comparing recipes you've already shown the user (listed
            below) - they're asking you to weigh in directly. Talk like a chef with a genuine
            opinion, not a spec-sheet comparison. Natural, confident, concise; avoid AI cliches
            and bullet-list-everything.

            STRICT RULES:
            - Compare ONLY the recipes listed below - never bring in a recipe that wasn't shown.
            - NEVER invent an ingredient, step, or nutrition fact not present in the data below.
            - Actually take a position: say which one you'd pick (or which fits their specific
              angle - healthier, cheaper, easier, fewer ingredients, freezes better, etc.) and why,
              using only what's in the data below (ingredient counts, steps, servings, rationale).
              If the data doesn't clearly settle their specific angle (e.g. they ask "which is
              cheaper" and there's no cost data), say that honestly rather than guessing a number.
            - Do not mention scores, algorithms, or the pipeline itself.
            """
            + RecipeContextFormatter.VOICE_GUIDANCE
            + """


            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            { "message": "your conversational response" }
            """;

    public LLMPrompt build(ConversationContext context, List<RecipeResponse> shownRecipes) {
        StringBuilder prompt = new StringBuilder();
        RecipeContextFormatter.appendRecentTurns(prompt, context);
        prompt.append("User's comparison request: ").append(context.getCurrentMessage()).append('\n');
        prompt.append("\nRecipe(s) already shown this session:\n");
        RecipeContextFormatter.appendRecipeBlocks(prompt, shownRecipes);
        return new LLMPrompt(SYSTEM_PROMPT, prompt.toString());
    }
}
