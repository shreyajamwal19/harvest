package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RetrievalPlan;

/**
 * One implementation per remaining response type (RECIPE, TECHNIQUE_ANSWER).
 * `plan` is always populated - Retrieval Planning now runs unconditionally
 * right after Context Assembly, since there is no Sufficiency Gate stage
 * upstream deciding whether to reach Composition at all.
 */
public interface ResponseComposer {
    ChefResponse compose(ConversationContext context, RetrievalPlan plan);
}
