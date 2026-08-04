package com.harvest.chef.retrieval;

import com.harvest.chef.reasoning.ReasoningMode;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Deterministic, rule-based classification of a follow-up turn about recipe(s) already shown
 * this session, into which {@link ReasoningMode} it needs - or empty if the message isn't a
 * follow-up at all (a fresh request, or a "more"/"another" continuation, which
 * {@link RetrievalPlanningService} already handles deterministically and which this detector
 * deliberately defers to rather than duplicating - see {@link #CONTINUATION_EXCLUSIONS}).
 *
 * Runs before {@link RetrievalPlanningService} in {@code CompositionService} - a non-empty
 * result skips retrieval entirely and routes straight to the AI Chef Reasoning Layer, grounded
 * in the recipe(s) already stored in session state. {@link ReasoningMode#TECHNIQUE_EXPLANATION}
 * is never produced here - that mode is selected separately, from {@link RetrievalPlan}'s own
 * TECHNIQUE intent classification in {@code CompositionService}.
 *
 * Same inline-heuristic style as {@link RetrievalPlanningService}: small, local keyword sets
 * rather than a separate NLU framework.
 */
@Component
public class FollowUpIntentDetector {

    // "Not this one, give me something different" - RetrievalPlanningService's own continuation
    // handling (excluding shown titles, reusing the last search) is the right deterministic tool
    // for these, not the reasoning-only follow-up path. Checked first so this detector never
    // hijacks a request for genuinely new recipes into a no-retrieval reasoning turn.
    private static final List<String> CONTINUATION_EXCLUSIONS = List.of(
            "show another", "show me another", "something else", "different recipe",
            "different one", "not this one", "not that one", "anything else", "what else",
            "another option", "other options", "dont like", "not a fan of that"
    );

    // Phrases that ask the model to justify/re-explain a pick already made.
    private static final List<String> EXPLANATION_PHRASES = List.of(
            "explain why", "why did you", "why do you recommend", "justify that", "justify this"
    );

    // Phrases that are only coherent as a direct comparison between recipes already shown.
    private static final List<String> COMPARISON_PHRASES = List.of(
            "which one", "which is", "which recipe", "which would you", "what would you cook",
            "compare these", "compare them", "compare those", "best for beginners",
            "beginner friendly", "which uses fewer", "which freezes better"
    );

    // Phrases that are only coherent as a follow-up adaptation - they already reference "the
    // thing already shown" grammatically, so no separate backreference check is needed. Bare
    // "without"/"instead" are included unconditionally (not just "without the"/"instead of the")
    // to catch shorthand like "without onions" or "with chicken instead" - safe because this
    // detector is only ever consulted by CompositionService when a prior recipe already exists
    // in session state (see CompositionService#tryComposeFollowUp), so a fresh first message
    // that happens to contain "instead" can never be misrouted here.
    private static final List<String> ADAPTATION_PHRASES = List.of(
            "make it", "make this", "double it", "halve it", "double this", "halve this",
            "cook for one", "cook for two", "cook for four", "cook for six", "cook for eight",
            "cook for ten", "cook for twelve", "without the", "without", "leave out the",
            "swap the", "substitute the", "instead of the", "instead", "replace butter",
            "replace the", "lower calories", "higher protein", "what if i remove",
            "what if i dont have", "no onions", "no garlic"
    );

    // Modifier concepts that only make sense applied to something already on the table -
    // deliberately NOT matched on their own (e.g. bare "vegetarian" also shows up in fresh
    // requests like "I want a vegetarian recipe"), only combined with a backreference below.
    private static final List<String> ADAPTATION_MODIFIER_WORDS = List.of(
            "vegetarian", "vegan", "gluten free", "gluten-free", "dairy free", "dairy-free",
            "double", "halve", "half the", "scale up", "scale down",
            "air fryer", "airfryer", "instant pot", "slow cooker",
            "reduce the calories", "fewer calories", "lower calorie", "healthier",
            "kid friendly", "kid-friendly", "less spicy", "spicier", "spicy",
            "easier", "simpler", "faster", "quicker", "cheaper", "cheap", "less expensive"
    );

    // Phrases only coherent as chef-coaching chat about a recipe already shown.
    private static final List<String> COACHING_PHRASES = List.of(
            "what should i serve", "serve with", "side dish", "side for", "pair with", "pairing",
            "can i freeze", "does it freeze", "how do i store", "meal prep", "restaurant quality",
            "mistakes should i avoid", "what mistakes", "would you cook this", "can my kids",
            "kids eat", "my daughter", "my son"
    );

    // Modifier concepts for coaching-flavored questions, again requiring a backreference on
    // their own so a fresh "how do I season a steak" request isn't misread as a follow-up.
    private static final List<String> COACHING_MODIFIER_WORDS = List.of(
            "freeze", "freezes", "reheat", "storage", "store it", "season", "seasoning",
            "texture", "overcooking", "resting"
    );

    // Backreference words/phrases that signal "the thing I was already shown", not a brand-new
    // subject.
    private static final List<String> BACKREFERENCES = List.of(
            "it", "that", "this", "that one", "this one",
            "the recipe", "that recipe", "this recipe", "same recipe", "same one"
    );

    /**
     * @param message the raw current message (not yet normalized)
     */
    public Optional<ReasoningMode> classify(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = " " + message.toLowerCase(Locale.ROOT).replace("'", "") + " ";

        if (CONTINUATION_EXCLUSIONS.stream().anyMatch(lower::contains)) {
            return Optional.empty();
        }

        if (EXPLANATION_PHRASES.stream().anyMatch(lower::contains)) {
            return Optional.of(ReasoningMode.RECIPE_EXPLANATION);
        }
        if (COMPARISON_PHRASES.stream().anyMatch(lower::contains)) {
            return Optional.of(ReasoningMode.RECIPE_COMPARISON);
        }
        if (ADAPTATION_PHRASES.stream().anyMatch(lower::contains)) {
            return Optional.of(ReasoningMode.RECIPE_ADAPTATION);
        }
        if (COACHING_PHRASES.stream().anyMatch(lower::contains)) {
            return Optional.of(ReasoningMode.CHEF_COACHING);
        }

        boolean hasBackreference = BACKREFERENCES.stream().anyMatch(ref -> lower.contains(" " + ref + " "));
        if (!hasBackreference) {
            return Optional.empty();
        }
        if (ADAPTATION_MODIFIER_WORDS.stream().anyMatch(lower::contains)) {
            return Optional.of(ReasoningMode.RECIPE_ADAPTATION);
        }
        if (COACHING_MODIFIER_WORDS.stream().anyMatch(lower::contains)) {
            return Optional.of(ReasoningMode.CHEF_COACHING);
        }

        // A bare backreference with no other signal ("can my kids eat this?", "would you cook
        // this?") is still a genuine follow-up about the shown recipe - chef coaching is the
        // right catch-all tone for open-ended chat like that.
        return Optional.of(ReasoningMode.CHEF_COACHING);
    }
}
