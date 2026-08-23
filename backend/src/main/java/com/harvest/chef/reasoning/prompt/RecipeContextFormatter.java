package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.ConversationTurn;
import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.knowledge.model.IngredientProfile;

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

    /**
     * Shared "sound like a person, not a template" instruction, appended to every reasoning-mode
     * system prompt (see each *PromptBuilder) rather than copy-pasted into five of them
     * separately. "Recent conversation" (appended just below this in every prompt via
     * {@link #appendRecentTurns}) already includes the assistant's own prior turns, so the model
     * has what it needs to actually notice and avoid repeating itself - this just tells it to.
     */
    public static final String VOICE_GUIDANCE = """

            VARIETY: Check "Recent conversation" below before you answer. If you (the assistant) \
            already opened a response with a similar phrase this session - "Great choice!", \
            "Here's why...", "This is a great pick because..." - open differently this time. Don't \
            restate a recipe's full title or its ingredient list back to the user; they can already \
            see the recipe card. Lead with the reasoning or the practical detail, not a recap.""";

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
        if (recipes == null || recipes.isEmpty()) {
            return;
        }
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
        if (recipes.size() > 1) {
            // If the user refers to one positionally ("the second one", "recipe 2", "the last
            // one") rather than by name, resolve it against the numbering above - don't ask
            // them to repeat which recipe they mean when the numbering already answers it.
            // Only reached when CompositionService's own deterministic FollowUpTargetResolver
            // couldn't confidently narrow it first (e.g. an ambiguous descriptor matching more
            // than one shown recipe) - this is the fallback, not the primary resolution path.
            prompt.append("(If the user refers to a recipe by position rather than name - "
                    + "\"the first one\", \"the second one\", \"recipe 2\", \"the last one\" - "
                    + "match it to the corresponding numbered item above. If they refer to it by a "
                    + "descriptive word instead - \"the chicken one\", \"the pasta one\" - match it to "
                    + "whichever numbered item's title or ingredients actually contains that word; if "
                    + "more than one could match, ask which one they mean rather than guessing.)\n");
        }
    }

    private static List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }

    /**
     * USDA-grounded nutrition data the Retrieval Orchestrator already fetched this turn (only
     * when {@code RetrievalPlan#needsNutritionGrounding} was true) but which nothing previously
     * consumed - real API calls whose result was silently discarded. Every figure here traces
     * back to {@link NutritionInfo#getSource()}; only include what's actually non-null.
     */
    public static void appendNutritionInfo(StringBuilder prompt, List<NutritionInfo> nutritionInfo) {
        if (nutritionInfo == null || nutritionInfo.isEmpty()) {
            return;
        }
        prompt.append("\nGrounded nutrition data (USDA-sourced - only state figures that appear here, ")
                .append("never estimate your own):\n");
        for (NutritionInfo info : nutritionInfo) {
            prompt.append("- ").append(info.getMatchedFoodName() != null ? info.getMatchedFoodName()
                    : info.getQueryTerm()).append(": ");
            List<String> parts = new java.util.ArrayList<>();
            if (info.getCalories() != null) {
                parts.add(info.getCalories() + " kcal");
            }
            if (info.getProteinGrams() != null) {
                parts.add(info.getProteinGrams() + "g protein");
            }
            if (info.getCarbsGrams() != null) {
                parts.add(info.getCarbsGrams() + "g carbs");
            }
            if (info.getFatGrams() != null) {
                parts.add(info.getFatGrams() + "g fat");
            }
            prompt.append(String.join(", ", parts)).append(" (source: ").append(info.getSource()).append(")\n");
        }
    }

    /**
     * Ingredient substitution/pairing/storage facts the Retrieval Orchestrator already fetched
     * this turn (only when {@code RetrievalPlan#needsIngredientIntelligence} was true) but which
     * nothing previously consumed for recipe-recommendation turns.
     */
    public static void appendIngredientProfiles(StringBuilder prompt, List<IngredientProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) {
            return;
        }
        prompt.append("\nIngredient facts already looked up (use only if relevant to the recommendation, ")
                .append("don't force it in):\n");
        for (IngredientProfile profile : profiles) {
            prompt.append("- ").append(profile.getName()).append(": ");
            List<String> parts = new java.util.ArrayList<>();
            if (profile.getSubstitutes() != null && !profile.getSubstitutes().isEmpty()) {
                parts.add("substitutes [" + String.join(", ", profile.getSubstitutes()) + "]");
            }
            if (profile.getStorageAdvice() != null && !profile.getStorageAdvice().isBlank()) {
                parts.add("storage: " + profile.getStorageAdvice());
            }
            if (profile.getShelfLife() != null && !profile.getShelfLife().isBlank()) {
                parts.add("shelf life: " + profile.getShelfLife());
            }
            prompt.append(String.join("; ", parts)).append('\n');
        }
    }
}
