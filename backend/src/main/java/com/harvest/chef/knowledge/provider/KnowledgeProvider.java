package com.harvest.chef.knowledge.provider;

import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;

/**
 * The common contract every knowledge provider implements. The Manager
 * only ever talks to providers through this shape (plus one typed
 * retrieval method per category) - it never knows how a provider actually
 * fetches its data.
 */
public interface KnowledgeProvider {

    KnowledgeProviderType getType();

    /** Short, stable identifier used in logs, attribution, and merging - e.g. "local", "themealdb". */
    String getName();

    /** Cheap, fast check - should not itself perform the real retrieval work. */
    boolean isAvailable();

    ProviderHealth healthStatus();

    /** Baseline track-record reliability for this provider, 0.0-1.0, used when merging/scoring. */
    double getReliability();
}
