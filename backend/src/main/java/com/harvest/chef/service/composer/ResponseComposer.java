package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RetrievalPlan;

/**
 * One implementation per allowed response type. `plan` is populated only
 * when the Sufficiency Gate returned SUFFICIENT and the Retrieval
 * Orchestrator has already run; composers that don't need it ignore it.
 */
public interface ResponseComposer {
    ChefResponse compose(ConversationContext context, GoalAssessment assessment, RetrievalPlan plan);
}
