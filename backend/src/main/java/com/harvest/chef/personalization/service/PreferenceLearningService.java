package com.harvest.chef.personalization.service;

import com.harvest.chef.personalization.entity.PreferenceCategory;
import com.harvest.chef.personalization.entity.PreferenceSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes explicit preference statements in ordinary conversation
 * ("I love spicy food", "I'm vegetarian", "I don't eat seafood") via
 * plain deterministic pattern matching - never an LLM call, so this can
 * never hallucinate a preference the user didn't actually state. Distinct
 * from {@code MemoryCommandDetector}, which handles direct commands
 * ("remember that...", "forget..."); this handles preferences volunteered
 * as ordinary statements mid-conversation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PreferenceLearningService {

    /**
     * One statement -> preference mapping this service recognized in a message.
     *
     * @param contradictsOpposite true only for a newly-stated restriction/intolerance/allergy
     *                            ("I can't eat X anymore", "I'm allergic to X") - a signal
     *                            strong enough that it should actively override any existing
     *                            opposite-polarity preference for the same value, not just sit
     *                            beside it. An ordinary "I don't like X" does not set this; a
     *                            passing taste dislike shouldn't erase a previously stated love
     *                            of X, but a stated new inability to eat X should.
     */
    public record LearnedPreference(PreferenceCategory category, String value, boolean positive,
                                     boolean contradictsOpposite) {
        public LearnedPreference(PreferenceCategory category, String value, boolean positive) {
            this(category, value, positive, false);
        }
    }

    private static final List<String> STOPWORDS_TO_TRIM =
            List.of("food", "foods", "meals", "dishes", "recipes", "cooking", "very much", "a lot");

    // Order matters: more specific patterns (vegetarian/vegan/allergy) are checked before the
    // generic "I love X" / "I hate X" catch-alls so e.g. "I'm vegetarian" isn't also parsed as a
    // FAVORITE_INGREDIENT for the literal word "vegetarian".
    private static final Pattern DIETARY_PATTERN =
            Pattern.compile("\\bi(?:'m| am)\\s+(vegetarian|vegan|pescatarian|gluten[- ]free|dairy[- ]free|keto|halal|kosher)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern DONT_EAT_PATTERN =
            Pattern.compile("\\bi\\s+don'?t\\s+eat\\s+([a-z ]{2,40}?)(?:[.!,]|$)", Pattern.CASE_INSENSITIVE);
    // A NEW inability/intolerance/allergy, distinct from a simple taste dislike (HATE_PATTERN) -
    // "I can't eat shellfish anymore" or "I'm allergic to peanuts" is often safety-relevant and,
    // critically, frequently CONTRADICTS an earlier positive statement about the same ingredient
    // ("I love shellfish" said last month). Handled as its own pattern (rather than folded into
    // HATE_PATTERN) so UserProfileService can react to it as an explicit contradiction, not just
    // another ordinary dislike.
    private static final Pattern NEW_RESTRICTION_PATTERN = Pattern.compile(
            "\\bi\\s+(?:can'?t|cannot|am unable to)\\s+eat\\s+([a-z ]{2,40}?)(?:\\s+anymore)?(?:[.!,]|$)"
                    + "|\\bi'?m\\s+(?:allergic|intolerant)\\s+to\\s+([a-z ]{2,40}?)(?:[.!,]|$)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern QUICK_MEALS_PATTERN =
            Pattern.compile("\\bi\\s+prefer\\s+(quick|fast|easy|slow|elaborate)\\s+(?:meals|cooking|recipes)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVING_SIZE_PATTERN =
            Pattern.compile("\\bi\\s+(?:usually\\s+)?cook\\s+for\\s+(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
    // Closed vocabulary of real cuisine names the recipe providers actually supply (Recipe#cuisine
    // locally, TheMealDB's strArea externally) - shared by CUISINE_PATTERN below and by
    // MemoryCommandService for "remember I like X" commands, so the same statement is
    // categorized as FAVORITE_CUISINE consistently regardless of how it's phrased.
    private static final Set<String> KNOWN_CUISINES = Set.of(
            "italian", "mexican", "indian", "chinese", "thai", "japanese", "french", "greek",
            "spanish", "korean", "vietnamese", "mediterranean", "american", "british", "irish",
            "moroccan", "caribbean", "middle eastern", "turkish", "lebanese", "ethiopian",
            "jamaican", "cajun", "southern", "tex-mex");
    // Checked before the generic LOVE_PATTERN catch-all so "I love Italian food" is stored as a
    // cuisine preference, not miscategorized as loving the literal ingredient "italian". Vocabulary
    // mirrors what the recipe providers actually supply as cuisine (Recipe#cuisine locally,
    // TheMealDB's strArea externally), kept intentionally closed rather than any free-form word
    // before "food"/"cuisine" - a closed list can't be tricked into learning nonsense.
    private static final Pattern CUISINE_PATTERN = Pattern.compile(
            "\\bi\\s+(?:love|really like|enjoy|prefer)\\s+(" + String.join("|", KNOWN_CUISINES) + ")"
                    + "\\s+(?:food|cuisine|cooking|dishes|recipes)?\\b",
            Pattern.CASE_INSENSITIVE);
    // Phase 7 - health goals (Part 3). Values are normalized to the exact tokens
    // RecipeScoringEngine#healthGoalAlignment recognizes; anything else stored under
    // HEALTH_GOAL is silently a no-op there rather than mis-scored, so this pattern set and
    // that method's switch must be kept in sync.
    private static final Pattern HEALTH_GOAL_PATTERN = Pattern.compile(
            "\\bi(?:'m| am)\\s+trying\\s+to\\s+(lose\\s+weight|gain\\s+weight|build\\s+muscle|gain\\s+muscle)\\b"
                    + "|\\bi\\s+want\\s+(?:to\\s+eat\\s+)?(more\\s+protein|less\\s+sodium|less\\s+salt|more\\s+fiber)\\b"
                    + "|\\bi'?m\\s+(watching\\s+my\\s+sodium|watching\\s+my\\s+salt|eating\\s+heart\\s+healthy|"
                    + "trying\\s+to\\s+eat\\s+healthier|trying\\s+to\\s+eat\\s+healthy)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOVE_PATTERN =
            Pattern.compile("\\bi\\s+(?:love|really like|enjoy)\\s+([a-z ]{2,40}?)(?:[.!,]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HATE_PATTERN =
            Pattern.compile("\\bi\\s+(?:hate|dislike|can'?t stand|don'?t like)\\s+([a-z ]{2,40}?)(?:[.!,]|$)",
                    Pattern.CASE_INSENSITIVE);

    /** Deterministic, LLM-free scan of a message for explicit preference statements. */
    public List<LearnedPreference> detect(String message) {
        if (message == null || message.isBlank()) {
            return List.of();
        }
        List<LearnedPreference> learned = new ArrayList<>();

        Matcher dietary = DIETARY_PATTERN.matcher(message);
        if (dietary.find()) {
            learned.add(new LearnedPreference(PreferenceCategory.DIETARY_RESTRICTION,
                    normalize(dietary.group(1)), true));
        }

        Matcher dontEat = DONT_EAT_PATTERN.matcher(message);
        if (dontEat.find()) {
            learned.add(new LearnedPreference(PreferenceCategory.DISLIKED_INGREDIENT,
                    normalize(dontEat.group(1)), false));
        }

        Matcher newRestriction = NEW_RESTRICTION_PATTERN.matcher(message);
        if (newRestriction.find()) {
            String raw = firstNonNullGroup(newRestriction);
            if (raw != null) {
                learned.add(new LearnedPreference(PreferenceCategory.DISLIKED_INGREDIENT,
                        normalize(raw), false, true));
            }
        }

        Matcher quick = QUICK_MEALS_PATTERN.matcher(message);
        if (quick.find()) {
            learned.add(new LearnedPreference(PreferenceCategory.PREFERRED_COOKING_DURATION,
                    normalize(quick.group(1)), true));
        }

        Matcher serving = SERVING_SIZE_PATTERN.matcher(message);
        if (serving.find()) {
            learned.add(new LearnedPreference(PreferenceCategory.PREFERRED_SERVING_SIZE,
                    serving.group(1), true));
        }

        Matcher healthGoal = HEALTH_GOAL_PATTERN.matcher(message);
        if (healthGoal.find()) {
            String raw = firstNonNullGroup(healthGoal);
            if (raw != null) {
                learned.add(new LearnedPreference(PreferenceCategory.HEALTH_GOAL, mapHealthGoal(raw), true));
            }
        }

        Matcher cuisine = CUISINE_PATTERN.matcher(message);
        if (cuisine.find()) {
            learned.add(new LearnedPreference(PreferenceCategory.FAVORITE_CUISINE,
                    normalize(cuisine.group(1)), true));
        }

        // Only apply the generic love/hate catch-alls if a more specific pattern above didn't
        // already claim this message, to avoid double-counting ("I don't eat seafood" shouldn't
        // also fire the generic hate pattern).
        if (learned.isEmpty()) {
            Matcher love = LOVE_PATTERN.matcher(message);
            if (love.find()) {
                learned.add(new LearnedPreference(PreferenceCategory.FAVORITE_INGREDIENT,
                        normalize(love.group(1)), true));
            }
            Matcher hate = HATE_PATTERN.matcher(message);
            if (hate.find()) {
                learned.add(new LearnedPreference(PreferenceCategory.DISLIKED_INGREDIENT,
                        normalize(hate.group(1)), false));
            }
        }

        if (!learned.isEmpty()) {
            log.info("[personalization] explicit statement(s) detected: {}", learned);
        }
        return learned;
    }

    /** Persists everything {@link #detect} found for this message, via UserProfileService. */
    /**
     * Each write is best-effort and isolated per learned preference: a DB hiccup (including a
     * benign unique-constraint race between two near-simultaneous turns for the same user -
     * user_preferences has a (user_id, category, value) unique constraint, so a losing
     * concurrent insert throws) must never fail the whole chat turn. Previously any exception
     * here propagated straight up through CompositionService into a 500, discarding an
     * otherwise fully-composed recipe/technique answer over what should be a silently-recoverable
     * personalization write.
     */
    public List<LearnedPreference> learnFromMessage(UserProfileService profileService, Long userId, String message) {
        List<LearnedPreference> learned = detect(message);
        List<LearnedPreference> persisted = new ArrayList<>();
        for (LearnedPreference lp : learned) {
            try {
                if (lp.positive()) {
                    profileService.reinforce(userId, lp.category(), lp.value(), PreferenceSource.EXPLICIT);
                } else if (lp.contradictsOpposite()) {
                    profileService.weakenAsContradiction(userId, lp.category(), lp.value(), PreferenceSource.EXPLICIT);
                } else {
                    profileService.weaken(userId, lp.category(), lp.value(), PreferenceSource.EXPLICIT);
                }
                // Only the successfully-persisted subset is returned: CompositionService uses
                // this list both to decide whether to reload the profile snapshot AND to tell
                // the user "Noted that you like X" - acknowledging something that failed to
                // save would be an honesty violation (never claim memory was saved when it
                // wasn't), so a failed write here must not appear as learned to the caller.
                persisted.add(lp);
            } catch (Exception e) {
                log.warn("Failed to persist learned preference userId={} category={} value='{}': {}",
                        userId, lp.category(), lp.value(), e.getMessage());
            }
        }
        return persisted;
    }

    private String normalize(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        for (String stop : STOPWORDS_TO_TRIM) {
            if (value.endsWith(" " + stop)) {
                value = value.substring(0, value.length() - stop.length()).trim();
            }
        }
        return value;
    }

    /**
     * Checks a raw phrase (e.g. an explicit "remember I like ___" command's argument) against
     * the same closed cuisine vocabulary {@link #CUISINE_PATTERN} uses, so "remember I like
     * Italian food" categorizes identically to saying "I love Italian food" in conversation
     * instead of falling back to FAVORITE_INGREDIENT just because it arrived via a different
     * command path. Returns empty when the phrase isn't a recognized cuisine name.
     */
    public java.util.Optional<String> matchCuisine(String rawPhrase) {
        if (rawPhrase == null || rawPhrase.isBlank()) {
            return java.util.Optional.empty();
        }
        String candidate = normalize(rawPhrase);
        return KNOWN_CUISINES.contains(candidate) ? java.util.Optional.of(candidate) : java.util.Optional.empty();
    }

    private String firstNonNullGroup(Matcher matcher) {
        for (int i = 1; i <= matcher.groupCount(); i++) {
            if (matcher.group(i) != null) {
                return matcher.group(i);
            }
        }
        return null;
    }

    /**
     * Canonicalizes free-form health-goal phrasing to the fixed vocabulary
     * {@code RecipeScoringEngine#healthGoalAlignment} understands. Anything not mapped here
     * simply never contributes a ranking signal - safer than guessing.
     */
    private String mapHealthGoal(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "lose weight" -> "weight_loss";
            case "gain weight" -> "weight_gain";
            case "build muscle", "gain muscle" -> "muscle_gain";
            case "more protein" -> "high_protein";
            case "less sodium", "less salt", "watching my sodium", "watching my salt" -> "low_sodium";
            case "more fiber" -> "high_fiber";
            case "eating heart healthy" -> "heart_healthy";
            default -> "general_healthy";
        };
    }
}
