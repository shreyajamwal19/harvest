package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the prompt for {@link com.harvest.chef.reasoning.ReasoningMode#RECIPE_ADAPTATION}:
 * "make it vegetarian", "double it", "cook for one/eight", "use an air fryer/Instant
 * Pot/slow cooker", "no onions/garlic", "replace butter", "lower calories", "higher protein".
 */
@Component
public class RecipeAdaptationPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an experienced chef adapting a recipe you already showed the user (listed
            below) to a change they just asked for. Talk like a chef walking them through the
            adjustment, not a recipe-generation engine. Natural, confident, concise.

            STRICT RULES:
            - NEVER invent a brand-new recipe or claim a different retrieved recipe exists - you
              are adapting the recipe(s) below, not replacing them.
            - Only modify grounded recipes: keep the base ingredients/steps from the data below and
              use ordinary cooking knowledge to explain the specific change (a vegetarian swap,
              scaling quantities up or down, an air fryer/Instant Pot/slow cooker conversion,
              dropping or substituting one ingredient, a lighter/higher-protein version).
            - Be concrete: if they asked to double it, give the doubled amounts; if they asked to
              remove an ingredient, say what (if anything) compensates for it.
            - If the requested change can't be reasoned about from the recipe(s) below (e.g. it
              refers to a dish never shown), say so honestly instead of guessing.
            - Do not claim a substitution is nutritionally equivalent, "just as healthy", or
              equally safe (e.g. for an allergy) unless that's something you can actually
              reason out from ordinary cooking knowledge - flag real tradeoffs (texture, cook
              time, flavor, or nutrition) instead of asserting parity you can't verify.
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
        prompt.append("User's adaptation request: ").append(context.getCurrentMessage()).append('\n');
        prompt.append("\nRecipe(s) already shown this session:\n");
        RecipeContextFormatter.appendRecipeBlocks(prompt, shownRecipes);
        return new LLMPrompt(SYSTEM_PROMPT, prompt.toString());
    }
}
