package com.harvest.chef.knowledge.provider;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.knowledge.model.ProviderResult;

import java.util.List;

public interface RecipeKnowledgeProvider extends KnowledgeProvider {

    ProviderResult<List<RecipeCandidate>> retrieve(String query);

    /**
     * Distinguishes the local database from external APIs for the Manager's
     * failover rule (local unavailable -> automatically try external).
     */
    default boolean isLocal() {
        return false;
    }
}
