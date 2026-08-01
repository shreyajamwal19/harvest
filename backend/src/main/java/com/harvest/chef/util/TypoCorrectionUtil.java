package com.harvest.chef.util;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic, dictionary-based typo tolerance for Retrieval Planning.
 * No ML/LLM involved (the reasoning pipeline is rule-based only) - just a
 * small curated correction map for common misspellings, plus a generic
 * edit-distance fallback against a fixed vocabulary of frequent ingredient
 * and control words.
 *
 * Deliberately conservative: the fuzzy fallback only fires for tokens of a
 * reasonable length within a small edit distance, so real (if unusual)
 * ingredient names - "harissa", "gochujang", etc. - are left untouched
 * rather than being mangled into an unrelated vocabulary word.
 */
public final class TypoCorrectionUtil {

    // Exact, known misspellings worth hard-coding rather than leaving to
    // edit-distance guessing (guarantees these always resolve correctly).
    private static final Map<String, String> KNOWN_TYPOS = Map.ofEntries(
            Map.entry("recpues", "recipes"),
            Map.entry("recpue", "recipe"),
            Map.entry("receipes", "recipes"),
            Map.entry("receipe", "recipe"),
            Map.entry("recepies", "recipes"),
            Map.entry("recepie", "recipe"),
            Map.entry("engredients", "ingredients"),
            Map.entry("engredient", "ingredient"),
            Map.entry("ingrediants", "ingredients"),
            Map.entry("ingrediant", "ingredient"),
            Map.entry("tomatos", "tomatoes"),
            Map.entry("tomatoe", "tomato"),
            Map.entry("potatos", "potatoes"),
            Map.entry("potatoe", "potato"),
            Map.entry("chiken", "chicken"),
            Map.entry("chikken", "chicken"),
            Map.entry("brocolli", "broccoli"),
            Map.entry("brocoli", "broccoli"),
            Map.entry("mayonaise", "mayonnaise"),
            Map.entry("spagetti", "spaghetti"),
            Map.entry("veggitable", "vegetable"),
            Map.entry("vegatable", "vegetable")
    );

    // Common ingredient + control-word vocabulary used only for the generic
    // fuzzy fallback below - not an exhaustive ingredient catalog.
    private static final Set<String> VOCABULARY = Set.of(
            "egg", "eggs", "tomato", "tomatoes", "potato", "potatoes", "onion", "onions",
            "garlic", "cheese", "chicken", "beef", "pork", "rice", "pasta", "flour",
            "sugar", "butter", "milk", "cream", "bread", "fish", "shrimp", "spinach",
            "mushroom", "mushrooms", "pepper", "peppers", "carrot", "carrots", "broccoli",
            "lettuce", "cucumber", "bacon", "ham", "sausage", "beans", "lentils", "corn",
            "avocado", "lemon", "lime", "apple", "banana", "strawberry", "strawberries",
            "chocolate", "vanilla", "cinnamon", "basil", "oregano", "thyme", "ginger",
            "honey", "yogurt", "oil", "salt", "vinegar", "recipe", "recipes",
            "ingredient", "ingredients", "breakfast", "lunch", "dinner", "dessert",
            "healthy", "quick", "easy", "spicy"
    );

    private TypoCorrectionUtil() {
    }

    /** Returns the corrected word, or the original word unchanged if no confident correction exists. */
    public static String correct(String word) {
        if (word == null || word.isBlank()) {
            return word;
        }
        String lower = word.toLowerCase(java.util.Locale.ROOT);

        if (VOCABULARY.contains(lower)) {
            return lower; // already a known-good word, nothing to correct
        }
        if (KNOWN_TYPOS.containsKey(lower)) {
            return KNOWN_TYPOS.get(lower);
        }
        if (lower.length() < 5) {
            return lower; // too short for edit-distance correction to be reliable
        }

        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : VOCABULARY) {
            int distance = levenshtein(lower, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }

        return (best != null && bestDistance <= 2) ? best : lower;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
