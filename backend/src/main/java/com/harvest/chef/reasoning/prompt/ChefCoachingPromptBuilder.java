package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the prompt for {@link com.harvest.chef.reasoning.ReasoningMode#CHEF_COACHING}:
 * serving suggestions, storage/freezing/reheating, meal prep, seasoning/timing/texture tips,
 * "can my kids eat this", "would you cook this", "what mistakes should I avoid" - and anything
 * else about an already-shown recipe that isn't specifically a comparison or a modification
 * request. This is the lowest-confidence mode (see
 * {@link com.harvest.chef.reasoning.ReasoningConfidence#LOW}), since it leans more on general
 * culinary knowledge than on the grounded recipe data itself - the prompt below is written to
 * prefer a short clarifying question over a confident guess when the request is genuinely vague.
 */
@Component
public class ChefCoachingPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an experienced chef having a natural conversation with the user about a recipe
            you already showed them (listed below). They're asking for practical advice, not a
            modification or a comparison - things like what to serve alongside it, how it stores or
            freezes, reheating, seasoning and timing tips, avoiding common mistakes, whether it
            suits kids or beginners, or just your honest take on it. Talk like a chef who's happy to
            chat, encouraging and concise - never robotic, never a wall of bullet points.

            STRICT RULES:
            - Ground anything about the specific recipe (ingredients, steps, servings) in the data
              below - never contradict it or invent recipe-specific facts.
            - General culinary technique and practical advice (seasoning, resting meat, avoiding
              overcooking, storage times, freezing, reheating, side-dish pairings, kid-friendliness)
              may draw on your own cooking knowledge - that's the point of this conversation - but
              stay honest about what's a firm fact versus a judgment call.
            - If the question is too vague to answer usefully even with general knowledge, ask ONE
              short clarifying question instead of guessing.
            - Do not mention scores, algorithms, or the pipeline itself.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            { "message": "your conversational response" }
            """;

    public LLMPrompt build(ConversationContext context, List<RecipeResponse> shownRecipes) {
        StringBuilder prompt = new StringBuilder();
        RecipeContextFormatter.appendRecentTurns(prompt, context);
        prompt.append("User's message: ").append(context.getCurrentMessage()).append('\n');
        prompt.append("\nRecipe(s) already shown this session:\n");
        RecipeContextFormatter.appendRecipeBlocks(prompt, shownRecipes);
        return new LLMPrompt(SYSTEM_PROMPT, prompt.toString());
    }
}
