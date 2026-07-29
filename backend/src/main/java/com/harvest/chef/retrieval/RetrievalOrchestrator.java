package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RetrievalBundle;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.provider.external.ExternalRecipeProvider;
import com.harvest.chef.provider.memory.UserMemoryProvider;
import com.harvest.chef.provider.nutrition.NutritionProvider;
import com.harvest.chef.provider.pantry.PantryProvider;
import com.harvest.chef.provider.recipe.LocalRecipeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates the knowledge providers a {@link RetrievalPlan} says are
 * worth calling. Local recipes and pantry/user-memory are cheap enough to
 * always check for a RECIPE intent; external recipes and nutrition are
 * gated behind the plan's flags so the Chef Brain doesn't make network
 * calls it doesn't need.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalOrchestrator {

    private final LocalRecipeProvider localRecipeProvider;
    private final ExternalRecipeProvider externalRecipeProvider;
    private final NutritionProvider nutritionProvider;
    private final PantryProvider pantryProvider;
    private final UserMemoryProvider userMemoryProvider;

    public RetrievalBundle retrieve(ConversationContext context, RetrievalPlan plan) {
        List<RecipeCandidate> recipeCandidates = new ArrayList<>();

        String query = plan.getSearchQuery() == null || plan.getSearchQuery().isBlank()
                ? context.getCurrentMessage()
                : plan.getSearchQuery();

        recipeCandidates.addAll(safeSearch(localRecipeProvider, query));

        if (plan.isNeedsExternalRecipes()) {
            recipeCandidates.addAll(safeSearch(externalRecipeProvider, query));
        }

        List<String> pantryItems = pantryProvider.currentPantryItems(plan.getMentionedIngredients());
        List<String> userMemoryNotes = userMemoryProvider.recentContextFor(context.getUserId(), context.getSessionId());

        List<NutritionInfo> nutritionInfo = plan.isNeedsNutritionGrounding()
                ? safeNutritionLookup(pantryItems.isEmpty() ? plan.getMentionedIngredients() : pantryItems)
                : List.of();

        return RetrievalBundle.builder()
                .recipeCandidates(recipeCandidates)
                .pantryItems(pantryItems)
                .userMemoryNotes(userMemoryNotes)
                .nutritionInfo(nutritionInfo)
                .build();
    }

    private List<RecipeCandidate> safeSearch(com.harvest.chef.provider.recipe.RecipeProvider provider, String query) {
        try {
            return provider.search(query);
        } catch (Exception e) {
            // Retrieval is an input to reasoning, not a hard dependency - one provider
            // failing should never break the whole cognitive loop.
            log.warn("Recipe provider '{}' failed: {}", provider.providerName(), e.getMessage());
            return List.of();
        }
    }

    private List<NutritionInfo> safeNutritionLookup(List<String> ingredients) {
        try {
            return nutritionProvider.lookup(ingredients);
        } catch (Exception e) {
            log.warn("Nutrition provider failed: {}", e.getMessage());
            return List.of();
        }
    }
}
