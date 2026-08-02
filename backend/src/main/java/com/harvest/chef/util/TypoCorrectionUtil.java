package com.harvest.chef.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, dictionary-based typo tolerance for Retrieval Planning.
 * No ML/LLM involved (the reasoning pipeline is rule-based only) - just a
 * small curated correction map for common misspellings, a hierarchical
 * repeated-character collapse for noisy/emphatic input ("eggggg",
 * "cheeeeeeese"), and a generic edit-distance fallback against a fixed
 * vocabulary of frequent ingredient and control words.
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

    /**
     * Common ingredient + control-word vocabulary. Public so other
     * deterministic scoring code (e.g. the popularity heuristic in
     * RecipeScoringEngine) can reuse the same "common ingredient" notion
     * instead of maintaining a second, duplicate list.
     */
    public static final Set<String> VOCABULARY = Set.of(
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

    /**
     * Returns the corrected word, or the original word unchanged if no
     * confident correction exists. Tries, in order: the word as typed;
     * a "light" collapse of any run of 3+ identical letters down to 2
     * (catches emphatic typing like "eggggg" without disturbing genuine
     * double letters, e.g. "egg" itself); a "full" collapse of any run of
     * 2+ identical letters down to 1 (catches noisier typos like
     * "tomatoss" -> "tomatos" -> "tomatoes", "ricce" -> "rice"); finally a
     * conservative edit-distance fallback on the original word.
     */
    public static String correct(String word) {
        if (word == null || word.isBlank()) {
            return word;
        }
        String lower = word.toLowerCase(Locale.ROOT);

        String exact = exactMatch(lower);
        if (exact != null) {
            return exact;
        }

        String lightCollapsed = collapseRuns(lower, 3, 2);
        if (!lightCollapsed.equals(lower)) {
            String exactLight = exactMatch(lightCollapsed);
            if (exactLight != null) {
                return exactLight;
            }
        }

        String fullCollapsed = collapseRuns(lower, 2, 1);
        if (!fullCollapsed.equals(lower) && !fullCollapsed.equals(lightCollapsed)) {
            String exactFull = exactMatch(fullCollapsed);
            if (exactFull != null) {
                return exactFull;
            }
        }

        if (lower.length() < 5) {
            return lower; // too short for edit-distance correction to be reliable
        }
        return fuzzyMatch(lower);
    }

    private static String exactMatch(String word) {
        if (VOCABULARY.contains(word)) {
            return word;
        }
        return KNOWN_TYPOS.get(word);
    }

    /**
     * Collapses any run of the same character whose length is at least
     * {@code minRunLength} down to exactly {@code collapseTo} occurrences.
     * Runs shorter than the threshold are left untouched - this is what
     * lets a two-stage (3->2, then 2->1) hierarchy protect real double
     * letters ("egg", "cheese") from being mangled by an overly aggressive
     * single pass.
     */
    private static String collapseRuns(String word, int minRunLength, int collapseTo) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < word.length()) {
            char c = word.charAt(i);
            int runLength = 1;
            while (i + runLength < word.length() && word.charAt(i + runLength) == c) {
                runLength++;
            }
            int keep = runLength >= minRunLength ? collapseTo : runLength;
            for (int k = 0; k < keep; k++) {
                result.append(c);
            }
            i += runLength;
        }
        return result.toString();
    }

    private static String fuzzyMatch(String lower) {
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
