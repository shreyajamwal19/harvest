package com.harvest.chef.personalization.service;

import com.harvest.chef.personalization.entity.PreferenceCategory;
import com.harvest.chef.personalization.entity.PreferenceSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    /** One statement -> preference mapping this service recognized in a message. */
    public record LearnedPreference(PreferenceCategory category, String value, boolean positive) {
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
    private static final Pattern QUICK_MEALS_PATTERN =
            Pattern.compile("\\bi\\s+prefer\\s+(quick|fast|easy|slow|elaborate)\\s+(?:meals|cooking|recipes)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVING_SIZE_PATTERN =
            Pattern.compile("\\bi\\s+(?:usually\\s+)?cook\\s+for\\s+(\\d{1,2})\\b", Pattern.CASE_INSENSITIVE);
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
    public List<LearnedPreference> learnFromMessage(UserProfileService profileService, Long userId, String message) {
        List<LearnedPreference> learned = detect(message);
        for (LearnedPreference lp : learned) {
            if (lp.positive()) {
                profileService.reinforce(userId, lp.category(), lp.value(), PreferenceSource.EXPLICIT);
            } else {
                profileService.weaken(userId, lp.category(), lp.value(), PreferenceSource.EXPLICIT);
            }
        }
        return learned;
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
