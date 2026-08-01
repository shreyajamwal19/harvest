package com.harvest.chef.retrieval;

import com.harvest.chef.dto.RecipeCandidate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Phase 4B - multi-factor recipe scoring, ranking, and result diversification.
 *
 * Deliberately split out of {@link RecipeEvaluationService} (which now just
 * orchestrates: filter -> score -> sort -> diversify -> shape the response)
 * so each ranking signal is its own small, independently testable method,
 * per the "keep scoring modular" requirement. Every method here is a pure
 * function of its inputs - no randomness, so identical inputs always
 * produce identical scores and ordering.
 *
 * Signals, roughly in the order a chef would reason about them:
 * 1. Ingredient match       - how many mentioned ingredients this recipe uses
 * 2. Pantry utilization     - how little shopping the recipe would require
 * 3. Missing-ingredient penalty
 * 4. Primary ingredient boost - is the searched ingredient central to the
 *    dish (in the title, or among the first few ingredients listed), not
 *    just incidentally present ("eggs" should favor an omelette over a
 *    cake that happens to use one egg)
 * 5. Title relevance        - fraction of mentioned ingredients that show
 *    up as whole words in the title
 * 6. Exact-match boost      - literal matches score slightly higher than
 *    synonym-derived ones (capsicum -> bell pepper still counts, just not
 *    quite as strongly as typing "bell pepper" directly)
 * 7. Multi-ingredient coverage bonus - matching several requested
 *    ingredients outranks matching only one, beyond what the plain overlap
 *    fraction already implies
 * 8. Completeness            - has a real description/servings/steps
 * 9. Source reliability
 *
 * Duplicate/diversity handling happens afterwards, once every candidate has
 * a score: {@link #selectDiverseTopResults} walks the score-sorted list and
 * skips near-duplicate titles so a results page isn't five omelette
 * variants back to back, backfilling from the skipped set only if there
 * aren't enough distinct candidates to fill the page.
 */
@Component
public class RecipeScoringEngine {

    private static final double WEIGHT_INGREDIENT_MATCH = 0.25;
    private static final double WEIGHT_PANTRY_UTILIZATION = 0.15;
    private static final double WEIGHT_MISSING_PENALTY = 0.10;
    private static final double WEIGHT_PRIMARY_INGREDIENT = 0.20;
    private static final double WEIGHT_TITLE_RELEVANCE = 0.10;
    private static final double WEIGHT_EXACT_MATCH = 0.05;
    private static final double WEIGHT_COVERAGE_BONUS = 0.05;
    private static final double WEIGHT_COMPLETENESS = 0.05;
    private static final double WEIGHT_SOURCE = 0.05;

    // Mirrors the reliability values the providers themselves already declare
    // via KnowledgeProvider.getReliability() - kept in sync, not reinvented.
    private static final Map<String, Double> SOURCE_RELIABILITY = Map.of(
            "local", 0.95,
            "themealdb", 0.60,
            "generated", 0.50
    );

    // Generic words stripped out when building a title's "signature" for
    // duplicate/diversity detection - otherwise almost every recipe title
    // would look similar just because they all say "easy" or "recipe".
    private static final Set<String> TITLE_NOISE_WORDS = Set.of(
            "the", "a", "an", "and", "with", "for", "of", "in", "on", "to",
            "easy", "quick", "best", "classic", "simple", "homemade", "style",
            "recipe", "recipes", "delicious", "perfect", "ultimate", "amazing"
    );

    /** Titles this similar (by shared significant words) are treated as near-duplicates. */
    private static final double DUPLICATE_TITLE_SIMILARITY = 0.6;

    private static final int PRIMARY_INGREDIENT_WINDOW = 3;

    /** Full scoring breakdown for one candidate against one request. */
    public record RecipeScore(RecipeCandidate candidate, double total, int matchedCount,
                               List<String> missingIngredients, List<String> explanations) {
    }

    /**
     * Scores a single candidate. Each factor is computed by its own method
     * and combined with a fixed weight - see the class-level signal list.
     */
    public RecipeScore score(RecipeCandidate candidate, List<String> mentioned, List<String> synonymResolved) {
        List<String> ingredients = candidate.getIngredients() == null ? List.of() : candidate.getIngredients();
        Set<String> synonyms = synonymResolved == null ? Set.of() : new HashSet<>(synonymResolved);

        int matchedCount = matchedIngredientCount(ingredients, mentioned);
        List<String> missing = missingIngredients(ingredients, mentioned);

        double ingredientMatch = ingredientMatchScore(mentioned, matchedCount);
        double pantryUtilization = pantryUtilizationScore(ingredients, matchedCount);
        double missingPenalty = missingPenaltyScore(ingredients, missing.size());
        double primaryIngredient = primaryIngredientScore(candidate, ingredients, mentioned);
        double titleRelevance = titleRelevanceScore(candidate, mentioned);
        double exactMatch = exactMatchScore(mentioned, synonyms, ingredients);
        double coverageBonus = coverageBonusScore(mentioned, matchedCount);
        double completeness = completenessScore(candidate);
        double reliability = sourceReliabilityScore(candidate);

        double total;
        if (mentioned.isEmpty()) {
            // No ingredient signal (a category/browse request) - fall back to
            // completeness + reliability, using the weight the ingredient-driven
            // signals would otherwise have claimed so scores stay comparable.
            double remainingWeight = WEIGHT_INGREDIENT_MATCH + WEIGHT_PANTRY_UTILIZATION
                    + WEIGHT_MISSING_PENALTY + WEIGHT_PRIMARY_INGREDIENT + WEIGHT_TITLE_RELEVANCE
                    + WEIGHT_EXACT_MATCH + WEIGHT_COVERAGE_BONUS + WEIGHT_SOURCE;
            total = WEIGHT_COMPLETENESS * completeness + remainingWeight * reliability;
        } else {
            total = WEIGHT_INGREDIENT_MATCH * ingredientMatch
                    + WEIGHT_PANTRY_UTILIZATION * pantryUtilization
                    + WEIGHT_PRIMARY_INGREDIENT * primaryIngredient
                    + WEIGHT_TITLE_RELEVANCE * titleRelevance
                    + WEIGHT_EXACT_MATCH * exactMatch
                    + WEIGHT_COVERAGE_BONUS * coverageBonus
                    + WEIGHT_COMPLETENESS * completeness
                    + WEIGHT_SOURCE * reliability
                    - WEIGHT_MISSING_PENALTY * missingPenalty;
        }

        List<String> explanations = buildExplanations(mentioned, matchedCount, missing.size(),
                pantryUtilization, primaryIngredient);

        return new RecipeScore(candidate, total, matchedCount, missing, explanations);
    }

    // ---------------------------------------------------------------- factor 1: ingredient match

    private int matchedIngredientCount(List<String> ingredients, List<String> mentioned) {
        String ingredientsLower = joinLower(ingredients);
        int count = 0;
        for (String m : mentioned) {
            if (containsAsWord(ingredientsLower, m)) {
                count++;
            }
        }
        return count;
    }

    private double ingredientMatchScore(List<String> mentioned, int matchedCount) {
        if (mentioned.isEmpty()) {
            return 0.0;
        }
        return matchedCount / (double) mentioned.size();
    }

    // ---------------------------------------------------------------- factor 2: pantry utilization

    private double pantryUtilizationScore(List<String> ingredients, int matchedCount) {
        if (ingredients.isEmpty()) {
            return 0.0;
        }
        return Math.min(1.0, matchedCount / (double) ingredients.size());
    }

    // ---------------------------------------------------------------- factor 3: missing-ingredient penalty

    private List<String> missingIngredients(List<String> ingredients, List<String> mentioned) {
        List<String> missing = new ArrayList<>();
        for (String line : ingredients) {
            String lower = line.toLowerCase(Locale.ROOT);
            boolean userHasIt = mentioned.stream().anyMatch(m -> containsAsWord(lower, m));
            if (!userHasIt) {
                missing.add(line);
            }
        }
        return missing;
    }

    private double missingPenaltyScore(List<String> ingredients, int missingCount) {
        if (ingredients.isEmpty()) {
            return 0.0;
        }
        return missingCount / (double) ingredients.size();
    }

    // ---------------------------------------------------------------- factor 4: primary ingredient boost

    /**
     * Rewards recipes where the mentioned ingredient(s) are central to the
     * dish, not just incidentally present - "eggs" should favor an omelette
     * (egg in the title) over a cake that lists one egg among a dozen other
     * ingredients.
     */
    private double primaryIngredientScore(RecipeCandidate candidate, List<String> ingredients, List<String> mentioned) {
        if (mentioned.isEmpty()) {
            return 0.0;
        }
        String titleLower = candidate.getTitle() == null ? "" : candidate.getTitle().toLowerCase(Locale.ROOT);
        boolean inTitle = mentioned.stream().anyMatch(m -> containsAsWord(titleLower, m));

        int window = Math.min(PRIMARY_INGREDIENT_WINDOW, ingredients.size());
        String earlyIngredientsLower = joinLower(ingredients.subList(0, window));
        boolean inEarlyIngredients = mentioned.stream().anyMatch(m -> containsAsWord(earlyIngredientsLower, m));

        double score = 0.0;
        if (inTitle) {
            score += 0.7;
        }
        if (inEarlyIngredients) {
            score += 0.3;
        }
        return Math.min(1.0, score);
    }

    // ---------------------------------------------------------------- factor 5: title relevance

    private double titleRelevanceScore(RecipeCandidate candidate, List<String> mentioned) {
        if (mentioned.isEmpty() || candidate.getTitle() == null) {
            return 0.0;
        }
        String titleLower = candidate.getTitle().toLowerCase(Locale.ROOT);
        long titleMatches = mentioned.stream().filter(m -> containsAsWord(titleLower, m)).count();
        return titleMatches / (double) mentioned.size();
    }

    // ---------------------------------------------------------------- factor 6: exact vs synonym match

    private double exactMatchScore(List<String> mentioned, Set<String> synonymResolved, List<String> ingredients) {
        if (mentioned.isEmpty()) {
            return 0.0;
        }
        String ingredientsLower = joinLower(ingredients);
        double weightedMatches = 0.0;
        for (String m : mentioned) {
            if (!containsAsWord(ingredientsLower, m)) {
                continue;
            }
            weightedMatches += synonymResolved.contains(m) ? 0.5 : 1.0;
        }
        return Math.min(1.0, weightedMatches / mentioned.size());
    }

    // ---------------------------------------------------------------- factor 7: multi-ingredient coverage

    /**
     * On top of the plain overlap fraction, explicitly rewards matching
     * several requested ingredients rather than just one - a recipe using
     * all of "eggs, cheese, milk" should clearly outrank one using only
     * "eggs", not just marginally.
     */
    private double coverageBonusScore(List<String> mentioned, int matchedCount) {
        if (mentioned.size() <= 1) {
            return matchedCount >= 1 ? 1.0 : 0.0;
        }
        if (matchedCount <= 1) {
            return 0.0;
        }
        return Math.min(1.0, (matchedCount - 1) / (double) (mentioned.size() - 1));
    }

    // ---------------------------------------------------------------- factor 8: completeness

    private double completenessScore(RecipeCandidate candidate) {
        int points = 0;
        if (candidate.getDescription() != null && !candidate.getDescription().isBlank()) {
            points++;
        }
        if (candidate.getServings() != null && candidate.getServings() > 0) {
            points++;
        }
        if (candidate.getSteps() != null && candidate.getSteps().size() >= 2) {
            points++;
        }
        if (candidate.getIngredients() != null && candidate.getIngredients().size() >= 2) {
            points++;
        }
        return points / 4.0;
    }

    // ---------------------------------------------------------------- factor 9: source reliability

    private double sourceReliabilityScore(RecipeCandidate candidate) {
        String source = candidate.getSource() == null ? "" : candidate.getSource();
        return SOURCE_RELIABILITY.getOrDefault(source, 0.4);
    }

    // ---------------------------------------------------------------- explanations

    private List<String> buildExplanations(List<String> mentioned, int matchedCount, int missingCount,
                                            double pantryUtilization, double primaryIngredientScore) {
        List<String> explanations = new ArrayList<>();
        if (mentioned.isEmpty()) {
            return explanations;
        }
        if (matchedCount == mentioned.size() && mentioned.size() > 1) {
            explanations.add("Uses all " + mentioned.size() + " ingredients you mentioned.");
        } else if (matchedCount > 0) {
            explanations.add("Uses " + matchedCount + " of the " + mentioned.size() + " ingredient(s) you mentioned.");
        }
        if (matchedCount > 0 && missingCount <= 2) {
            explanations.add(missingCount == 0
                    ? "Nothing else to buy."
                    : "Only " + missingCount + " ingredient(s) missing.");
        }
        if (pantryUtilization >= 0.5) {
            explanations.add("Excellent pantry match.");
        }
        if (primaryIngredientScore >= 0.7 && mentioned.size() == 1) {
            explanations.add(capitalize(mentioned.get(0)) + " is the primary ingredient.");
        }
        return explanations;
    }

    // ---------------------------------------------------------------- diversity / duplicate handling

    /**
     * Walks the score-sorted candidates and skips ones whose title is a
     * near-duplicate of one already selected, so a results page isn't five
     * nearly-identical variants of the same dish. Backfills from skipped
     * (duplicate) candidates, still in score order, if there aren't enough
     * distinct ones to fill the page - diversity should never mean
     * returning fewer results than requested when more exist.
     */
    public List<RecipeScore> selectDiverseTopResults(List<RecipeScore> scoredDescending, int maxResults) {
        List<RecipeScore> selected = new ArrayList<>();
        List<RecipeScore> skippedDuplicates = new ArrayList<>();
        List<Set<String>> selectedSignatures = new ArrayList<>();

        for (RecipeScore candidate : scoredDescending) {
            if (selected.size() >= maxResults) {
                break;
            }
            Set<String> signature = titleSignature(candidate.candidate().getTitle());
            boolean isDuplicate = selectedSignatures.stream()
                    .anyMatch(existing -> jaccardSimilarity(existing, signature) >= DUPLICATE_TITLE_SIMILARITY);

            if (isDuplicate) {
                skippedDuplicates.add(candidate);
            } else {
                selected.add(candidate);
                selectedSignatures.add(signature);
            }
        }

        for (RecipeScore fallback : skippedDuplicates) {
            if (selected.size() >= maxResults) {
                break;
            }
            selected.add(fallback);
        }

        return selected;
    }

    private Set<String> titleSignature(String title) {
        if (title == null) {
            return Set.of();
        }
        String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
        Set<String> signature = new LinkedHashSet<>();
        for (String word : normalized.split("\\s+")) {
            if (word.length() >= 3 && !TITLE_NOISE_WORDS.contains(word)) {
                signature.add(word);
            }
        }
        return signature;
    }

    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : intersection.size() / (double) union.size();
    }

    // ---------------------------------------------------------------- shared helpers

    private String joinLower(List<String> lines) {
        return String.join(" ", lines).toLowerCase(Locale.ROOT);
    }

    /**
     * Whole-word containment rather than plain substring matching, so
     * searching "egg" doesn't spuriously match "eggplant". Multi-word
     * tokens (e.g. a synonym-resolved "bell pepper") fall back to plain
     * substring matching, since word-boundary regex doesn't extend
     * naturally across an embedded space.
     */
    private boolean containsAsWord(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        if (needle.contains(" ")) {
            return haystack.contains(needle.toLowerCase(Locale.ROOT));
        }
        return Pattern.compile("\\b" + Pattern.quote(needle.toLowerCase(Locale.ROOT)) + "\\b")
                .matcher(haystack)
                .find();
    }

    private String capitalize(String word) {
        if (word == null || word.isBlank()) {
            return word;
        }
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
