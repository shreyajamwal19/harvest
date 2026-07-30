package com.harvest.chef.knowledge.provider;

import com.harvest.chef.knowledge.model.ProviderResult;

import java.util.List;

public interface PantryKnowledgeProvider extends KnowledgeProvider {
    ProviderResult<List<String>> retrieve(List<String> mentionedIngredients);
}
