package com.harvest.chef.knowledge.provider;

import com.harvest.chef.knowledge.model.IngredientProfile;
import com.harvest.chef.knowledge.model.ProviderResult;

import java.util.List;

public interface IngredientIntelligenceProvider extends KnowledgeProvider {
    ProviderResult<List<IngredientProfile>> retrieve(List<String> ingredientNames);
}
