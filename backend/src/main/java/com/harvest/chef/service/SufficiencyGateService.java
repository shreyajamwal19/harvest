package com.harvest.chef.service;

import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.GoalSufficiency;
import org.springframework.stereotype.Service;

/**
 * Stage 3 - Sufficiency Gate.
 *
 * Deterministic on purpose: it does not call the model. It takes the Goal
 * Reasoning stage's verdict and applies hard rules before letting it drive
 * Composition, so a malformed or inconsistent judgement can never produce a
 * broken response type downstream.
 */
@Service
public class SufficiencyGateService {

    public GoalSufficiency decide(GoalAssessment assessment) {
        if (!assessment.isCookingRelated()) {
            return GoalSufficiency.NON_ACTIONABLE;
        }

        GoalSufficiency reported = assessment.getSufficiency();
        if (reported == null) {
            return GoalSufficiency.NON_ACTIONABLE;
        }

        boolean missingInfoIsEmpty = assessment.getMissingInformation() == null
                || assessment.getMissingInformation().isBlank();

        if (reported == GoalSufficiency.INSUFFICIENT && missingInfoIsEmpty) {
            // A clarifying question needs something concrete to ask about. Without it,
            // this isn't really an "insufficient" case - treat it as non-actionable
            // rather than asking an empty question.
            return GoalSufficiency.NON_ACTIONABLE;
        }

        return reported;
    }
}
