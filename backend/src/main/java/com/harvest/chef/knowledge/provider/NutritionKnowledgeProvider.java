package com.harvest.chef.knowledge.provider;

import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.knowledge.model.ProviderResult;

import java.util.List;

public interface NutritionKnowledgeProvider extends KnowledgeProvider {
    ProviderResult<List<NutritionInfo>> retrieve(List<String> ingredientNames);
}
