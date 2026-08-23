package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RetrievalBundle;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.knowledge.manager.KnowledgeProviderManager;
import com.harvest.chef.knowledge.model.IngredientProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Knows HOW and WHEN to retrieve, based on the {@link RetrievalPlan} - but
 * not WHERE any of it comes from. Every actual provider call goes through
 * the {@link KnowledgeProviderManager}, which owns provider selection,
 * parallel execution, failure isolation, and merging.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetrievalOrchestrator {

    private final KnowledgeProviderManager knowledgeProviderManager;

    public RetrievalBundle retrieve(ConversationContext context, RetrievalPlan plan) {
        // A blank searchQuery here is a deliberate signal from Retrieval Planning
        // (no ingredient or category could be extracted), not a bug to paper
        // over - LocalRecipeProvider treats it as an honest catalog browse
        // rather than a literal-text search that would almost certainly match
        // nothing across the imported dataset.
        String query = plan.getSearchQuery() == null ? "" : plan.getSearchQuery();

        List<RecipeCandidate> recipeCandidates =
                knowledgeProviderManager.retrieveRecipes(query, plan.isNeedsExternalRecipes());

        List<String> userMemoryNotes =
                knowledgeProviderManager.retrieveUserMemory(context.getUserId(), context.getSessionId());

        List<String> nutritionTargets = plan.getMentionedIngredients() == null ? List.of() : plan.getMentionedIngredients();
        List<NutritionInfo> nutritionInfo = plan.isNeedsNutritionGrounding()
                ? knowledgeProviderManager.retrieveNutrition(nutritionTargets)
                : List.of();

        List<IngredientProfile> ingredientProfiles = plan.isNeedsIngredientIntelligence()
                ? knowledgeProviderManager.retrieveIngredientIntelligence(nutritionTargets)
                : List.of();

        return RetrievalBundle.builder()
                .recipeCandidates(recipeCandidates)
                .userMemoryNotes(userMemoryNotes)
                .nutritionInfo(nutritionInfo)
                .ingredientProfiles(ingredientProfiles)
                .build();
    }
}
