package com.harvest.chef.util;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic, dictionary-based typo tolerance for Retrieval Planning.
 * No ML/LLM involved (the reasoning pipeline is rule-based only) - just a
 * small curated correction map for common misspellings and informal
 * shorthand, a hierarchical repeated-character collapse for noisy/emphatic
 * input ("eggggg", "cheeeeeeese"), a generic singular/plural bridge, and a
 * transposition-aware edit-distance fallback against a fixed vocabulary of
 * frequent ingredient and control words.
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
            Map.entry("vegatable", "vegetable"),
            Map.entry("avacado", "avocado"),
            Map.entry("avacados", "avocados"),
            Map.entry("cheeze", "cheese"),
            Map.entry("spinich", "spinach"),
            Map.entry("spinnach", "spinach"),
            Map.entry("yogourt", "yogurt"),
            Map.entry("yoghurt", "yogurt"),
            Map.entry("zuchini", "zucchini"),
            Map.entry("zuchinni", "zucchini"),
            Map.entry("cauliflour", "cauliflower"),
            // Informal, unambiguous shorthand worth recognizing directly rather than leaving to
            // edit-distance guessing (which "chkn" is too short and vowel-less for anyway).
            Map.entry("chkn", "chicken"),
            Map.entry("veggies", "vegetables"),
            Map.entry("veggie", "vegetable"),
            // Short (<5-char) dish-shape typos: the edit-distance fallback below deliberately
            // never fires on words this short (too unreliable at that length - see `correct`),
            // so these need an explicit entry the same way "chkn" does, or they're never
            // corrected at all and searched as a literal nonsense token instead.
            Map.entry("boal", "bowl")
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
            "healthy", "quick", "easy", "spicy", "zucchini", "cauliflower",
            // Dish-shape/format words. Missing these meant a typo in the dish word itself
            // (not an ingredient) never got corrected, e.g. "burrito boal" was searched
            // literally as the token "boal" - which matches nothing - so the candidate pool
            // was effectively driven by "burrito" alone and surfaced an unrelated burrito
            // recipe instead of anything resembling a bowl.
            "bowl", "cake", "salad", "soup", "stew", "chili", "casserole", "curry",
            "pizza", "burrito", "taco", "tacos", "burger", "sandwich", "wrap", "skillet",
            "muffin", "muffins", "pancake", "pancakes", "waffle", "waffles", "cookie",
            "cookies", "pie", "bar", "bars", "roll", "rolls", "noodles", "noodle",
            "sauce", "gravy", "dressing", "smoothie", "toast", "omelette", "omelet",
            "quiche", "risotto", "lasagna", "meatballs", "meatloaf", "truffle", "alfredo"
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
        String known = KNOWN_TYPOS.get(word);
        if (known != null) {
            return known;
        }
        return pluralBridge(word);
    }

    /**
     * Generic singular/plural bridging, tried only after an exact and known-typo match have
     * both failed. Strips a common plural suffix ("-ies" -> "-y", "-es", "-s") and checks
     * whether the resulting singular is itself an exact vocabulary word; separately, tries
     * adding "s" in case the word is a singular form but only the plural is in the vocabulary.
     * This resolves plural typos not individually hardcoded in {@link #KNOWN_TYPOS} (e.g. a
     * mistyped "strawberrys") without growing that map indefinitely. Deliberately requires the
     * bridged form to already be a real vocabulary entry, so it can never invent a match for an
     * unrelated word - it can only ever collapse two spellings of something already known.
     */
    private static String pluralBridge(String word) {
        if (word.length() < 4) {
            return null;
        }
        if (word.endsWith("ies") && word.length() > 5) {
            String singular = word.substring(0, word.length() - 3) + "y";
            if (VOCABULARY.contains(singular)) {
                return singular;
            }
        }
        if (word.endsWith("es") && word.length() > 4) {
            String singular = word.substring(0, word.length() - 2);
            if (VOCABULARY.contains(singular)) {
                return singular;
            }
        }
        if (word.endsWith("s") && !word.endsWith("ss")) {
            String singular = word.substring(0, word.length() - 1);
            if (VOCABULARY.contains(singular)) {
                return singular;
            }
        }
        String plural = word + "s";
        if (VOCABULARY.contains(plural)) {
            return plural;
        }
        return null;
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

    /**
     * A flat "distance <= 2" ceiling is far too loose for a short word: 2 edits on a 5-letter
     * word is a 40% character change, which routinely lands on a real but unrelated word rather
     * than a typo of the intended one - e.g. "pesto" (a real, if uncommon, ingredient not in this
     * vocabulary) is distance 2 from "pasta" and would otherwise be silently mangled into it.
     * Scaling the ceiling by length keeps the fallback doing what its own documentation promises
     * (leaving unusual-but-real ingredient names alone) while still catching genuine typos on
     * longer words ("brocoli" -> "broccoli" is distance 1 either way).
     */
    private static int maxEditDistanceFor(int wordLength) {
        if (wordLength <= 6) {
            return 1;
        }
        return 2;
    }

    /**
     * Ties are broken deterministically - by smallest length difference from the input, then
     * lexicographically - rather than by whichever vocabulary word {@code Set.of()} happens to
     * iterate first. Set iteration order isn't guaranteed stable across JVM instances, so without
     * this a genuine tie could silently resolve to a different correction on different server
     * restarts even for the exact same typo.
     */
    private static String fuzzyMatch(String lower) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        int bestLengthDiff = Integer.MAX_VALUE;
        int ceiling = maxEditDistanceFor(lower.length());

        for (String candidate : VOCABULARY) {
            int distance = restrictedDamerauLevenshtein(lower, candidate);
            if (distance > ceiling) {
                continue;
            }
            int lengthDiff = Math.abs(lower.length() - candidate.length());
            boolean better = distance < bestDistance
                    || (distance == bestDistance && lengthDiff < bestLengthDiff)
                    || (distance == bestDistance && lengthDiff == bestLengthDiff
                        && (best == null || candidate.compareTo(best) < 0));
            if (better) {
                bestDistance = distance;
                bestLengthDiff = lengthDiff;
                best = candidate;
            }
        }
        return best != null ? best : lower;
    }

    /**
     * Levenshtein distance with one addition: an adjacent-character transposition ("hcicken" vs
     * "chicken") counts as a single edit instead of two substitutions. This is the "restricted"
     * (optimal string alignment) variant, not full Damerau-Levenshtein - it doesn't allow a
     * transposed pair to be edited again afterwards, which is a non-issue at the short word
     * lengths and small distance ceilings this is used at (see {@link #maxEditDistanceFor}), and
     * keeps the DP simple. Scoring an adjacent swap as one edit rather than two doesn't loosen
     * false-positive risk - it just scores a genuine one-swap typo the way a person would
     * actually perceive it, freeing that budget for real length-appropriate matches instead of
     * being consumed by a transposition.
     */
    private static int restrictedDamerauLevenshtein(String a, String b) {
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
                int value = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    value = Math.min(value, dp[i - 2][j - 2] + 1);
                }
                dp[i][j] = value;
            }
        }
        return dp[a.length()][b.length()];
    }
}
