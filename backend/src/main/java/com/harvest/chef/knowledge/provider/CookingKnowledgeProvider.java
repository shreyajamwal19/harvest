package com.harvest.chef.knowledge.provider;

import com.harvest.chef.knowledge.model.ProviderResult;

public interface CookingKnowledgeProvider extends KnowledgeProvider {
    ProviderResult<String> retrieve(String question, String interpretedGoal);
}
