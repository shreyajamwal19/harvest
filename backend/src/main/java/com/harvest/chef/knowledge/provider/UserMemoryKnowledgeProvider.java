package com.harvest.chef.knowledge.provider;

import com.harvest.chef.knowledge.model.ProviderResult;

import java.util.List;

public interface UserMemoryKnowledgeProvider extends KnowledgeProvider {
    ProviderResult<List<String>> retrieve(Long userId, Long excludingSessionId);
}
