package com.harvest.chef.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic, rule-based detection of a follow-up turn about a recipe
 * already shown this session ("make it vegetarian", "double it", "can I
 * use an air fryer", "reduce the calories") as opposed to a fresh recipe
 * or technique request. Runs before {@link RetrievalPlanningService} in
 * {@code CompositionService} - a positive match skips retrieval entirely
 * and routes straight to the AI Chef Reasoning Layer, grounded only in the
 * recipe(s) already stored in session state.
 *
 * Same inline-heuristic style as {@link RetrievalPlanningService}: small,
 * local keyword sets rather than a separate NLU framework.
 */
@Component
public class FollowUpIntentDetector {

    // Phrases that are only coherent as a follow-up - they already reference
    // "the thing already shown" grammatically, so no separate backreference
    // check is needed for these.
    private static final List<String> SELF_CONTAINED_FOLLOW_UP_PHRASES = List.of(
            "make it", "make this", "double it", "halve it", "double this", "halve this",
            "which one is easiest", "which is easiest", "easiest one",
            "without the", "leave out the", "swap the", "substitute the", "instead of the"
    );

    // Modifier concepts that only make sense applied to something already on
    // the table - deliberately NOT matched on their own (e.g. bare
    // "vegetarian" also shows up in fresh requests like "I want a
    // vegetarian recipe"), only combined with a backreference below.
    private static final List<String> MODIFIER_WORDS = List.of(
            "vegetarian", "vegan", "gluten free", "gluten-free", "dairy free", "dairy-free",
            "double", "halve", "half the", "scale up", "scale down",
            "air fryer", "airfryer", "instant pot", "slow cooker",
            "reduce the calories", "fewer calories", "lower calorie", "healthier",
            "kid friendly", "kid-friendly", "less spicy", "spicier",
            "easier", "simpler", "faster", "quicker"
    );

    // Backreference words/phrases that signal "the thing I was already
    // shown", not a brand-new subject.
    private static final List<String> BACKREFERENCES = List.of(
            "it", "that", "this", "that one", "this one",
            "the recipe", "that recipe", "this recipe", "same recipe", "same one"
    );

    /**
     * @param message the raw current message (not yet normalized)
     */
    public boolean isFollowUp(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = " " + message.toLowerCase(Locale.ROOT).replace("'", "") + " ";

        boolean isSelfContained = SELF_CONTAINED_FOLLOW_UP_PHRASES.stream().anyMatch(lower::contains);
        if (isSelfContained) {
            return true;
        }

        boolean hasModifier = MODIFIER_WORDS.stream().anyMatch(lower::contains);
        boolean hasBackreference = BACKREFERENCES.stream()
                .anyMatch(ref -> lower.contains(" " + ref + " "));

        return hasModifier && hasBackreference;
    }
}
