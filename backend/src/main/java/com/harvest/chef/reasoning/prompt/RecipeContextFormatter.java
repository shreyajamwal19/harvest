package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.ConversationTurn;
import com.harvest.chef.dto.RecipeResponse;

import java.util.List;

/**
 * Formats the two pieces of grounding data every prompt builder needs: recent conversation
 * turns (this is what gives every mode "conversation continuation" for free - see
 * {@link com.harvest.chef.reasoning.ReasoningMode}) and structured recipe data (title,
 * ingredients, steps, missing ingredients, rationale - never raw entities). Centralized here so
 * no individual prompt builder duplicates this formatting.
 */
public final class RecipeContextFormatter {

    private static final int MAX_RECENT_TURNS = 4;

    private RecipeContextFormatter() {
    }

    public static void appendRecentTurns(StringBuilder prompt, ConversationContext context) {
        List<ConversationTurn> turns = context.getRecentTurns();
        if (turns == null || turns.isEmpty()) {
            return;
        }
        int fromIndex = Math.max(0, turns.size() - MAX_RECENT_TURNS);
        prompt.append("Recent conversation:\n");
        for (ConversationTurn turn : turns.subList(fromIndex, turns.size())) {
            prompt.append(turn.getRole()).append(": ").append(turn.getContent()).append('\n');
        }
        prompt.append('\n');
    }

    public static void appendRecipeBlocks(StringBuilder prompt, List<RecipeResponse> recipes) {
        int index = 1;
        for (RecipeResponse recipe : recipes) {
            prompt.append(index++).append(") ").append(recipe.getTitle())
                    .append(" [source: ").append(recipe.getSource()).append(']').append('\n');
            if (recipe.getServings() != null) {
                prompt.append("   Servings: ").append(recipe.getServings()).append('\n');
            }
            prompt.append("   Ingredients: ")
                    .append(String.join(", ", nullSafe(recipe.getIngredients()))).append('\n');
            if (recipe.getMissingIngredients() != null && !recipe.getMissingIngredients().isEmpty()) {
                prompt.append("   Missing from what the user has: ")
                        .append(String.join(", ", recipe.getMissingIngredients())).append('\n');
            }
            if (recipe.getRationale() != null && !recipe.getRationale().isBlank()) {
                prompt.append("   Why it was retrieved: ").append(recipe.getRationale()).append('\n');
            }
            if (recipe.getSteps() != null && !recipe.getSteps().isEmpty()) {
                prompt.append("   Steps: ").append(String.join(" | ", recipe.getSteps())).append('\n');
            }
        }
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }
}
