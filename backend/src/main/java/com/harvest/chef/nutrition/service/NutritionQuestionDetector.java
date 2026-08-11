package com.harvest.chef.nutrition.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Recognizes a follow-up question asking about the nutrition of the recipe already shown
 * ("is this healthy?", "high protein?", "how much fiber?") deterministically, so it can be
 * routed to {@code NutritionQuestionComposer} for a USDA-grounded answer instead of the LLM's
 * free-form chef-coaching path, which has no real nutrition data to reason from and would risk
 * inventing plausible-sounding numbers ("Never hallucinate nutrition" - Part 2).
 *
 * Checked in {@code CompositionService#tryComposeFollowUp} before the general
 * {@link com.harvest.chef.retrieval.FollowUpIntentDetector} classification, the same way
 * Phase 6A/6B's deterministic commands are checked before retrieval - a more specific,
 * groundable question always wins over a generic catch-all.
 */
@Component
public class NutritionQuestionDetector {

    public enum NutritionQuestionType {
        CALORIES, PROTEIN, CARBS, FAT, FIBER, SODIUM, GENERAL_HEALTHY
    }

    private static final Pattern CALORIES = Pattern.compile(
            "how many calories|calorie count|calories (?:in|does) this|how much energy");
    private static final Pattern PROTEIN = Pattern.compile(
            "high(?:er)? in protein|how much protein|protein content|is this high protein|"
                    + "is (?:it|this) protein.rich");
    private static final Pattern CARBS = Pattern.compile(
            "low carb|how many carbs|carb content|how much carbs?|is this low.carb");
    private static final Pattern FAT = Pattern.compile(
            "how much fat|fat content|is this low.fat|is (?:it|this) fatty|how fatty|grams? of fat");
    private static final Pattern FIBER = Pattern.compile("how much fiber|fiber content|high in fiber");
    private static final Pattern SODIUM = Pattern.compile(
            "how much sodium|sodium content|is this low sodium|too much salt|how salty");
    private static final Pattern GENERAL_HEALTHY = Pattern.compile(
            "is (?:this|it) healthy|how healthy is (?:this|it)|is (?:this|it) a healthy (?:option|choice|meal)|"
                    + "nutrition(?:al)? (?:info|information|value|facts)|is (?:this|it) good for me");

    public Optional<NutritionQuestionType> detect(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT).replace("'", "");

        if (CALORIES.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.CALORIES);
        }
        if (PROTEIN.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.PROTEIN);
        }
        if (CARBS.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.CARBS);
        }
        if (FAT.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.FAT);
        }
        if (FIBER.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.FIBER);
        }
        if (SODIUM.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.SODIUM);
        }
        if (GENERAL_HEALTHY.matcher(lower).find()) {
            return Optional.of(NutritionQuestionType.GENERAL_HEALTHY);
        }
        return Optional.empty();
    }
}
