package com.harvest.chef.planning.service;

import com.harvest.chef.retrieval.RecipeCategoryClassifier.Category;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes meal-planning requests ("plan dinners this week", "meal prep for five days",
 * "give me lunches for work") deterministically and extracts a day count (defaulting/clamping
 * to the 1/3/5/7 options called for) plus an optional meal-type hint. No LLM involved - which
 * days get which recipes is entirely {@code MealPlanningService}'s job.
 */
@Component
public class MealPlanRequestDetector {

    public record MealPlanRequest(int days, Category mealType) {
    }

    private static final Pattern TRIGGER = Pattern.compile(
            "meal\\s*plan|meal\\s*prep|plan\\s+(?:my\\s+)?(?:dinners|lunches|breakfasts|meals)|"
                    + "plan\\s+.*\\bweek\\b|lunches?\\s+for\\s+work|(?:dinners|lunches|meals)\\s+for\\s+the\\s+week",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DIGIT_DAYS = Pattern.compile("(\\d+)[- ]?days?", Pattern.CASE_INSENSITIVE);
    private static final List<String> NUMBER_WORDS = List.of(
            "zero", "one", "two", "three", "four", "five", "six", "seven");

    public Optional<MealPlanRequest> detect(String message) {
        if (message == null || message.isBlank() || !TRIGGER.matcher(message).find()) {
            return Optional.empty();
        }
        String lower = message.toLowerCase(Locale.ROOT);

        int days = extractDayCount(lower);
        Category mealType = extractMealType(lower);

        return Optional.of(new MealPlanRequest(clampToSupportedPlanSize(days), mealType));
    }

    private int extractDayCount(String lower) {
        Matcher digits = DIGIT_DAYS.matcher(lower);
        if (digits.find()) {
            return Integer.parseInt(digits.group(1));
        }
        for (int i = NUMBER_WORDS.size() - 1; i >= 1; i--) {
            if (lower.contains(NUMBER_WORDS.get(i) + " day")) {
                return i;
            }
        }
        if (lower.contains("week")) {
            return 7; // "this week" / "for the week" with no explicit count
        }
        if (lower.contains("work")) {
            return 5; // "lunches for work" - a work week
        }
        return 3; // generic "meal prep" / "meal plan" with no other signal
    }

    /** VALIDATION calls for 1/3/5/7-day plans specifically - snap any parsed count to the nearest. */
    private int clampToSupportedPlanSize(int requested) {
        int[] supported = {1, 3, 5, 7};
        int closest = supported[0];
        for (int size : supported) {
            if (Math.abs(size - requested) < Math.abs(closest - requested)) {
                closest = size;
            }
        }
        return closest;
    }

    private Category extractMealType(String lower) {
        if (lower.contains("lunch")) {
            return Category.LUNCH;
        }
        if (lower.contains("breakfast")) {
            return Category.BREAKFAST;
        }
        if (lower.contains("dinner") || lower.contains("supper")) {
            return Category.DINNER;
        }
        return null;
    }
}
