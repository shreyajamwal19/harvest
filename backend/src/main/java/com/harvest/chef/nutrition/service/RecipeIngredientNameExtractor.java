package com.harvest.chef.nutrition.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns a full recipe ingredient line ("2 cups all-purpose flour", "1 tsp salt") into a plain
 * food name suitable for a USDA search query ("all-purpose flour", "salt"). Deterministic
 * regex-based stripping - never a guess at what the ingredient "really" is.
 *
 * Deliberately not shared with {@code PantryCommandDetector}'s own leading-quantity parsing:
 * that class parses a short, free-form spoken command argument ("2 lbs chicken"), this parses
 * a structured recipe-dataset ingredient line that also carries prep notes and unit vocabulary
 * a pantry command never would ("2 cups all-purpose flour, sifted") - similar shape, different
 * enough inputs that sharing the same regex would weaken both.
 */
@Component
public class RecipeIngredientNameExtractor {

    private static final Pattern LEADING_QUANTITY_AND_UNIT = Pattern.compile(
            "^[\\d./\\s]*\\s*(?:cups?|tbsps?|tablespoons?|tsps?|teaspoons?|oz|ounces?|lbs?|pounds?|"
                    + "grams?|kilograms?|kg|g|ml|milliliters?|l|liters?|pinch(?:es)?|dash(?:es)?|"
                    + "cloves?|slices?|cans?|packages?|pkgs?|sticks?|bunch(?:es)?|heads?)\\b\\s*(?:of\\s+)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEADING_BARE_NUMBER = Pattern.compile("^[\\d./\\s]+\\s*");
    private static final Pattern TRAILING_PREP_NOTE = Pattern.compile(
            ",\\s*(?:diced|chopped|minced|sliced|grated|crushed|peeled|melted|softened|"
                    + "room temperature|to taste|for garnish|optional|beaten|shredded|cubed).*$",
            Pattern.CASE_INSENSITIVE);

    /** Best-effort plain food name for USDA lookup, or the trimmed original if nothing to strip. */
    public String extractFoodName(String ingredientLine) {
        if (ingredientLine == null || ingredientLine.isBlank()) {
            return "";
        }
        String cleaned = ingredientLine.trim();
        cleaned = TRAILING_PREP_NOTE.matcher(cleaned).replaceFirst("");
        cleaned = LEADING_QUANTITY_AND_UNIT.matcher(cleaned).replaceFirst("");
        cleaned = LEADING_BARE_NUMBER.matcher(cleaned).replaceFirst("");
        return cleaned.trim().toLowerCase(Locale.ROOT);
    }

    /** Extracts a food name for every non-blank line, dropping anything that stripped to nothing. */
    public List<String> extractAll(List<String> ingredientLines) {
        List<String> names = new ArrayList<>();
        if (ingredientLines == null) {
            return names;
        }
        for (String line : ingredientLines) {
            String name = extractFoodName(line);
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }
}
