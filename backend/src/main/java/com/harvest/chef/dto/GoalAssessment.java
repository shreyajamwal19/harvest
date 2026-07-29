package com.harvest.chef.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Internal-only result of the Goal Reasoning stage. Never serialized directly
 * to the client - it's the contract between Goal Reasoning, the Sufficiency
 * Gate, and Composition.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalAssessment {

    private boolean cookingRelated;
    private GoalSufficiency sufficiency;
    private String interpretedGoal;
    private String missingInformation;
    private String reasoningNote;
}
