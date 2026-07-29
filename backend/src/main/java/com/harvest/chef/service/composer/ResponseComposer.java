package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;

/** One implementation per allowed response type: Recipe, Clarifying Question, Honest Non-Answer. */
public interface ResponseComposer {
    ChefResponse compose(ConversationContext context, GoalAssessment assessment);
}
