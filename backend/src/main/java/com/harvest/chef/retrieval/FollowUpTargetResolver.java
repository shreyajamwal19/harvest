package com.harvest.chef.retrieval;

import com.harvest.chef.dto.RecipeResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves WHICH of several previously-shown recipes a follow-up message is actually about -
 * "the second one", "recipe 2", "the last one", "the chicken one", "the pasta one" - so that a
 * follow-up turn ({@link FollowUpIntentDetector}) grounds against, and echoes back, only the
 * recipe the user meant rather than the entire previously-shown set.
 *
 * {@link FollowUpIntentDetector} already recognizes an ordinal reference well enough to classify
 * the turn as SOME kind of follow-up, and the follow-up prompts ask the model itself to resolve
 * position-by-number - but neither of those actually narrows which recipe object is being
 * discussed, and neither handles a descriptive reference ("the chicken one") at all. This class
 * is deliberately separate from (and deterministic, unlike) the AI Chef Reasoning Layer: getting
 * this wrong silently (grounding the LLM in - and returning - the wrong recipe) is worse than not
 * resolving it, so this only ever returns a target when the match is unambiguous, and returns
 * empty (letting the caller fall back to the full previously-shown list, as before) otherwise.
 *
 * Only meaningful when more than one recipe was shown; a single previously-shown recipe is
 * already unambiguous and callers should not consult this at all in that case.
 */
@Component
public class FollowUpTargetResolver {

    // Same word/number forms FollowUpIntentDetector's ORDINAL_BACKREFERENCE recognizes, but here
    // captured so the actual index can be resolved rather than just detected.
    private static final Pattern ORDINAL_WORD = Pattern.compile(
            "\\b(?:the\\s+)?(first|second|third|fourth|fifth|sixth|last)\\s+(?:one|recipe|option|dish)\\b");
    private static final Pattern ORDINAL_DIGIT_SUFFIXED = Pattern.compile(
            "\\b(?:the\\s+)?([1-6])(?:st|nd|rd|th)\\s+(?:one|recipe|option|dish)\\b");
    private static final Pattern ORDINAL_NUMBERED = Pattern.compile(
            "\\b(?:recipe|option|number|#)\\s*([1-6])\\b");

    // "the <word(s)> one" / "the <word(s)> recipe" / "the <word(s)> dish" - a descriptive
    // reference to whichever shown recipe's title or ingredients that word actually names.
    // Deliberately excludes bare pronouns/backreference words and generic filler so it doesn't
    // misfire on "the first one" (handled above) or "the same one".
    private static final Pattern DESCRIPTIVE_REFERENCE = Pattern.compile(
            "\\bthe\\s+([a-z][a-z\\s]{1,30}?)\\s+(?:one|recipe|option|dish)\\b");

    private static final List<String> ORDINAL_WORDS =
            List.of("first", "second", "third", "fourth", "fifth", "sixth");

    // Words that are structurally part of the reference phrase itself, not a description of a
    // recipe - must be excluded from the descriptive-match branch or "the same one"/"the other
    // one"/"the last one" would spuriously "match" against a recipe literally titled that.
    private static final List<String> NON_DESCRIPTIVE_FILLER =
            List.of("same", "other", "previous", "last", "first", "second", "third",
                    "fourth", "fifth", "sixth", "next");

    /**
     * @param message         the raw current message (not yet normalized)
     * @param previouslyShown recipes shown earlier this session; must have more than one entry
     *                        for any resolution to be attempted
     * @return the single recipe the message unambiguously refers to, or empty if there's no
     *         reference, the reference doesn't resolve to exactly one recipe, or there's only
     *         one recipe to begin with (nothing to disambiguate)
     */
    public Optional<RecipeResponse> resolve(String message, List<RecipeResponse> previouslyShown) {
        if (message == null || message.isBlank() || previouslyShown == null || previouslyShown.size() < 2) {
            return Optional.empty();
        }
        String lower = " " + message.toLowerCase(Locale.ROOT).replace("'", "") + " ";

        Optional<RecipeResponse> ordinal = resolveOrdinal(lower, previouslyShown);
        if (ordinal.isPresent()) {
            return ordinal;
        }
        return resolveDescriptive(lower, previouslyShown);
    }

    private Optional<RecipeResponse> resolveOrdinal(String lower, List<RecipeResponse> shown) {
        Matcher wordMatcher = ORDINAL_WORD.matcher(lower);
        if (wordMatcher.find()) {
            String word = wordMatcher.group(1);
            int index = "last".equals(word) ? shown.size() - 1 : ORDINAL_WORDS.indexOf(word);
            return indexInBounds(index, shown) ? Optional.of(shown.get(index)) : Optional.empty();
        }
        Matcher digitSuffixed = ORDINAL_DIGIT_SUFFIXED.matcher(lower);
        if (digitSuffixed.find()) {
            int index = Integer.parseInt(digitSuffixed.group(1)) - 1;
            return indexInBounds(index, shown) ? Optional.of(shown.get(index)) : Optional.empty();
        }
        Matcher numbered = ORDINAL_NUMBERED.matcher(lower);
        if (numbered.find()) {
            int index = Integer.parseInt(numbered.group(1)) - 1;
            return indexInBounds(index, shown) ? Optional.of(shown.get(index)) : Optional.empty();
        }
        return Optional.empty();
    }

    private boolean indexInBounds(int index, List<RecipeResponse> shown) {
        return index >= 0 && index < shown.size();
    }

    private Optional<RecipeResponse> resolveDescriptive(String lower, List<RecipeResponse> shown) {
        Matcher matcher = DESCRIPTIVE_REFERENCE.matcher(lower);
        if (!matcher.find()) {
            return Optional.empty();
        }
        String descriptor = matcher.group(1).trim();
        if (descriptor.isEmpty() || NON_DESCRIPTIVE_FILLER.contains(descriptor)) {
            return Optional.empty();
        }

        List<RecipeResponse> matches = shown.stream()
                .filter(recipe -> describesRecipe(descriptor, recipe))
                .toList();

        // Only resolve when exactly one recipe matches the descriptor - if the descriptor is
        // ambiguous (matches none, or matches several, e.g. two chicken dishes both shown), it's
        // safer to fall back to grounding in the full list than to silently guess wrong.
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    private boolean describesRecipe(String descriptor, RecipeResponse recipe) {
        String title = recipe.getTitle() == null ? "" : recipe.getTitle().toLowerCase(Locale.ROOT);
        if (containsAsWord(title, descriptor)) {
            return true;
        }
        String ingredients = recipe.getIngredients() == null
                ? ""
                : String.join(" ", recipe.getIngredients()).toLowerCase(Locale.ROOT);
        return containsAsWord(ingredients, descriptor);
    }

    // Word-boundary match, not bare substring - same tradeoff as RecipeScoringEngine's own
    // containsAsWord, so e.g. a descriptor of "egg" doesn't spuriously match "eggplant".
    private boolean containsAsWord(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        if (needle.contains(" ")) {
            return haystack.contains(needle);
        }
        return Pattern.compile("\\b" + Pattern.quote(needle) + "\\b").matcher(haystack).find();
    }
}
