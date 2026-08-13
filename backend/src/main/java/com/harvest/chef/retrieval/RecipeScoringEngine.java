package com.harvest.chef.retrieval;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.personalization.dto.UserProfileSnapshot;
import com.harvest.chef.personalization.entity.PreferenceCategory;
import com.harvest.chef.retrieval.RecipeCategoryClassifier.Category;
import com.harvest.chef.util.TypoCorrectionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Phase 4B/4C - multi-factor recipe scoring, ranking, and result
 * diversification, tuned to feel like an experienced chef's
 * recommendations rather than a keyword search.
 *
 * Deliberately split out of {@link RecipeEvaluationService} (which just
 * orchestrates: filter -> score -> sort -> diversify -> shape the
 * response) so each ranking signal is its own small, independently
 * testable method. Every method here is a pure function of its inputs -
 * no randomness, so identical inputs always produce identical scores and
 * ordering.
 *
 * Signal groups:
 * - Ingredient-driven (only meaningful when the request actually
 *   mentioned ingredients): match fraction, pantry utilization, missing
 *   penalty, tiered ingredient importance (primary/secondary/minor/
 *   garnish - see {@link #ingredientImportanceScore}), title relevance,
 *   exact-vs-synonym weighting, multi-ingredient coverage bonus.
 * - Intent-driven (meaningful even for ingredient-free, conversational
 *   requests like "need dinner" or "healthy recipes" -
 *   {@link RetrievalPlan#getPreferenceTags()}): meal-type fit, dietary
 *   fit, budget fit, occasion fit - see {@link #intentAlignmentScore}.
 * - Global: recipe completeness, a popularity heuristic derived from
 *   metadata (no fabricated rating/prep-time - the dataset doesn't carry
 *   one), and source reliability.
 *
 * Duplicate/diversity handling happens afterwards, once every candidate
 * has a score: {@link #selectDiverseTopResults} walks the score-sorted
 * list and skips near-duplicate titles (now also considering inferred
 * category) so a results page isn't five omelette variants back to back.
 */
@Component
@RequiredArgsConstructor
public class RecipeScoringEngine {

    // Ingredient-driven signal weights.
    private static final double WEIGHT_INGREDIENT_MATCH = 0.18;
    private static final double WEIGHT_PANTRY_UTILIZATION = 0.10;
    private static final double WEIGHT_MISSING_PENALTY = 0.08;
    private static final double WEIGHT_INGREDIENT_IMPORTANCE = 0.20;
    private static final double WEIGHT_TITLE_RELEVANCE = 0.08;
    private static final double WEIGHT_EXACT_MATCH = 0.04;
    private static final double WEIGHT_COVERAGE_BONUS = 0.05;

    // Intent + global signal weights - these apply regardless of whether the
    // request mentioned any ingredient, so a pure "need dinner" still ranks
    // sensibly.
    private static final double WEIGHT_INTENT_ALIGNMENT = 0.15;
    private static final double WEIGHT_POPULARITY = 0.08;
    private static final double WEIGHT_SOURCE = 0.04;

    // Phase 6A - personalization signals. Additive on top of the deterministic base score
    // above; small weights by design, since the current request always outranks stored
    // preferences (WEIGHT_INTENT_ALIGNMENT / WEIGHT_INGREDIENT_* stay dominant).
    private static final double WEIGHT_PERSONALIZATION = 0.10;
    /** Smart Variety - a soft penalty only, never a hard exclusion. */
    private static final double WEIGHT_SMART_VARIETY = 0.05;

    // Deliberately far larger than WEIGHT_PERSONALIZATION: a dietary restriction is safety/
    // standing-constraint-relevant (Ω-2 Part 16), not a taste nudge, so unlike favorite/disliked
    // ingredients it must not be able to lose out to a strong ingredient-match score. Scaled by
    // the preference's own confidence so a low-confidence/inferred restriction still applies but
    // less forcefully than a confidently-known, EXPLICIT one - see personalizationScore's
    // header comment for why this lives outside the blended [-1,1] average.
    private static final double WEIGHT_DIETARY_RESTRICTION = 1.2;

    // Phase 7 - explicitly negated ingredients from the CURRENT message ("no mushrooms").
    // Deliberately the largest single weight in the engine: this is the user's own explicit
    // intent for the request being answered right now, so it should reliably outrank
    // everything else (personalization, pantry, popularity) without being an outright hard
    // filter that could zero out every candidate and produce nothing.
    // A "no nuts"/"avoid dairy" exclusion is a hard user constraint, not a soft preference -
    // the doc comment on the call site already says it's "never suppressed by anything else
    // in the plan," but the previous weight (0.5) couldn't actually guarantee that: every
    // other WEIGHT_* constant above sums to roughly ~1.07 at their combined maximum, so a
    // recipe with a near-perfect ingredient/title/intent match could still outrank a mediocre
    // non-excluded alternative even while containing something the user explicitly excluded.
    // 1.5 makes a single exclusion hit (penalty >= 0.7 -> subtraction >= 1.05) reliably larger
    // than that entire achievable range, so an excluded-ingredient recipe only ever surfaces
    // when literally nothing else is available - graceful degradation is preserved (nothing
    // is hard-filtered out of the candidate list) while exclusion effectively dominates ranking.
    private static final double WEIGHT_EXCLUSION = 1.5;

    // Phase 6B - pantry-awareness signals. Same additive philosophy: small weights, the
    // current request's own ingredient/intent signals always dominate.
    private static final double WEIGHT_PANTRY_COVERAGE = 0.08;
    private static final double WEIGHT_EXPIRY_USAGE = 0.10;

    private static final double INGREDIENT_SIGNAL_BUDGET = WEIGHT_INGREDIENT_MATCH + WEIGHT_PANTRY_UTILIZATION
            + WEIGHT_INGREDIENT_IMPORTANCE + WEIGHT_TITLE_RELEVANCE + WEIGHT_EXACT_MATCH + WEIGHT_COVERAGE_BONUS;

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

    // ---------------------------------------------------------------- intent keyword sets

    private static final Set<String> GARNISH_INDICATORS = Set.of(
            "garnish", "to serve", "for serving", "optional", "for topping",
            "to garnish", "for garnish", "sprinkle of", "pinch of", "dash of", "to taste"
    );

    private static final Set<String> VEGETABLE_KEYWORDS = Set.of(
            "spinach", "broccoli", "carrot", "zucchini", "kale", "lettuce", "tomato", "cucumber",
            "pepper", "cabbage", "cauliflower", "asparagus", "greens", "vegetable", "onion",
            "mushroom", "peas", "squash"
    );
    private static final Set<String> LEAN_PROTEIN_KEYWORDS = Set.of(
            "chicken breast", "turkey", "tofu", "fish", "salmon", "tuna", "egg", "beans",
            "lentils", "chickpea", "greek yogurt", "quinoa"
    );
    private static final Set<String> MEAT_FISH_KEYWORDS = Set.of(
            "chicken", "beef", "pork", "bacon", "sausage", "ham", "fish", "shrimp", "turkey",
            "lamb", "salmon", "tuna", "meat", "gelatin"
    );
    private static final Set<String> ANIMAL_PRODUCT_KEYWORDS = Set.of(
            "milk", "cheese", "butter", "cream", "egg", "yogurt", "honey"
    );
    private static final Set<String> HIGH_CARB_KEYWORDS = Set.of(
            "rice", "pasta", "bread", "sugar", "flour", "potato", "noodle", "tortilla"
    );
    private static final Set<String> PROTEIN_KEYWORDS = Set.of(
            "chicken", "beef", "egg", "tofu", "beans", "lentil", "fish", "turkey", "pork",
            "shrimp", "quinoa", "greek yogurt", "protein", "salmon", "tuna"
    );
    // Phase 7 - health-goal heuristics (Part 3). Ingredient/preparation keywords, never a
    // real nutrient count, so recipes are never described in specific gram/mg terms from
    // this - only "sodium-heavy ingredients" style qualitative language (see
    // healthGoalAlignment and its explanation text below).
    private static final Set<String> SODIUM_HEAVY_KEYWORDS = Set.of(
            "bacon", "sausage", "ham", "soy sauce", "pickle", "pickled", "cured", "canned",
            "salted", "brine", "cheese", "processed", "deli meat", "instant noodle"
    );
    private static final Set<String> FRIED_OR_HEAVY_KEYWORDS = Set.of(
            "fried", "deep-fried", "deep fried", "battered", "crispy", "butter", "cream",
            "mayonnaise", "mayo", "bacon", "sausage"
    );
    private static final Set<String> SPICY_KEYWORDS = Set.of(
            "chili", "chilli", "cayenne", "jalapeno", "jalapeño", "hot sauce", "sriracha",
            "habanero", "spicy", "curry", "szechuan", "pepper flakes", "hot pepper"
    );

    // Dedicated exclusion vocabularies (distinct from the scoring keyword sets above, which are
    // tuned for positive-preference alignment, not exclusion safety). Each covers the way an
    // ingredient actually appears in a recipe's title/ingredient text, not the category name
    // itself - recipes almost never literally say "dairy" or "meat", and word-boundary matching
    // means "nuts" never matches inside "walnuts"/"peanuts" as one token, so these need their
    // own real vocabulary rather than reusing the bare category word.
    private static final Set<String> DAIRY_KEYWORDS = Set.of(
            "milk", "cheese", "butter", "cream", "yogurt", "buttermilk", "ghee", "sour cream",
            "cream cheese", "half and half", "whipped cream", "parmesan", "mozzarella", "cheddar",
            "ricotta", "condensed milk", "evaporated milk", "custard"
    );
    private static final Set<String> MEAT_ONLY_KEYWORDS = Set.of(
            "chicken", "beef", "pork", "bacon", "sausage", "ham", "lamb", "turkey", "meat",
            "veal", "duck", "steak", "ground beef", "chorizo", "prosciutto", "pepperoni"
    );
    private static final Set<String> SEAFOOD_KEYWORDS = Set.of(
            "fish", "shrimp", "salmon", "tuna", "shellfish", "crab", "lobster", "cod", "tilapia",
            "seafood", "anchovy", "anchovies", "scallop", "scallops", "mussel", "mussels",
            "clam", "clams", "squid", "calamari"
    );
    private static final Set<String> NUT_KEYWORDS = Set.of(
            "walnut", "walnuts", "peanut", "peanuts", "almond", "almonds", "cashew", "cashews",
            "pecan", "pecans", "pistachio", "pistachios", "hazelnut", "hazelnuts", "macadamia",
            "macadamias", "brazil nut", "brazil nuts", "pine nut", "pine nuts", "nut", "nuts",
            "peanut butter", "almond butter", "nut butter"
    );
    private static final Set<String> GLUTEN_PROXY_KEYWORDS = Set.of(
            "flour", "wheat", "bread", "pasta", "noodle", "noodles", "breadcrumbs", "barley",
            "couscous", "soy sauce", "beer", "cracker", "crackers", "tortilla"
    );

    // Excluded "trait" terms (e.g. "spicy" from "not spicy") that have an existing keyword-set
    // definition elsewhere in this engine get checked against that richer vocabulary instead of
    // the bare literal word - a recipe almost never spells out the word "spicy" itself, it just
    // contains cayenne/jalapeño/hot sauce/etc. Reuses the same keyword set the positive "spicy"
    // preference tag already scores against (see dietaryAlignment) rather than a second,
    // separately-maintained list, and this map is where any future trait-word/keyword-set pair
    // would be added, so the exclusion path automatically benefits the same way.
    //
    // "oil" is deliberately NOT mapped here: unlike "spicy"/"greasy", "oil" is itself a literal
    // ingredient word that shows up verbatim in ingredient lists ("2 tbsp olive oil", "vegetable
    // oil") - routing it through FRIED_OR_HEAVY_KEYWORDS (which contains no literal "oil" term)
    // meant "no oil" never matched a recipe that plainly listed oil as an ingredient. It falls
    // through to the literal whole-word check in matchesExcludedTerm instead, same as any other
    // named ingredient.
    private static final Map<String, Set<String>> EXCLUDABLE_TRAIT_KEYWORDS = Map.ofEntries(
            Map.entry("spicy", SPICY_KEYWORDS),
            Map.entry("spice", SPICY_KEYWORDS),
            Map.entry("heat", SPICY_KEYWORDS),
            Map.entry("dairy", DAIRY_KEYWORDS),
            Map.entry("lactose", DAIRY_KEYWORDS),
            Map.entry("meat", MEAT_ONLY_KEYWORDS),
            Map.entry("red meat", MEAT_ONLY_KEYWORDS),
            Map.entry("fish", SEAFOOD_KEYWORDS),
            Map.entry("seafood", SEAFOOD_KEYWORDS),
            Map.entry("shellfish", SEAFOOD_KEYWORDS),
            Map.entry("nuts", NUT_KEYWORDS),
            Map.entry("nut", NUT_KEYWORDS),
            Map.entry("tree nuts", NUT_KEYWORDS),
            Map.entry("gluten", GLUTEN_PROXY_KEYWORDS),
            Map.entry("wheat", GLUTEN_PROXY_KEYWORDS),
            Map.entry("carbs", HIGH_CARB_KEYWORDS),
            Map.entry("carbohydrates", HIGH_CARB_KEYWORDS),
            Map.entry("sodium", SODIUM_HEAVY_KEYWORDS),
            Map.entry("salt", SODIUM_HEAVY_KEYWORDS),
            Map.entry("fried", FRIED_OR_HEAVY_KEYWORDS),
            Map.entry("greasy", FRIED_OR_HEAVY_KEYWORDS)
    );

    private final RecipeCategoryClassifier categoryClassifier;

    /** Full scoring breakdown for one candidate against one request. */
    public record RecipeScore(RecipeCandidate candidate, double total, int matchedCount,
                               List<String> missingIngredients, List<String> explanations) {
    }

    private enum ImportanceTier {
        PRIMARY(1.0), SECONDARY(0.6), MINOR(0.3), GARNISH(0.1), ABSENT(0.0);

        final double weight;

        ImportanceTier(double weight) {
            this.weight = weight;
        }
    }

    /** Scores a single candidate against the full retrieval plan (ingredients + intent). */
    public RecipeScore score(RecipeCandidate candidate, RetrievalPlan plan) {
        List<String> mentioned = plan.getMentionedIngredients() == null ? List.of() : plan.getMentionedIngredients();
        Set<String> synonymResolved = plan.getSynonymResolvedIngredients() == null
                ? Set.of() : new HashSet<>(plan.getSynonymResolvedIngredients());
        Set<String> preferenceTags = plan.getPreferenceTags() == null ? Set.of() : plan.getPreferenceTags();
        List<String> ingredients = candidate.getIngredients() == null ? List.of() : candidate.getIngredients();

        int matchedCount = matchedIngredientCount(ingredients, mentioned);
        List<String> missing = missingIngredients(ingredients, mentioned);
        Set<Category> categories = categoryClassifier.classify(candidate);
        String combinedText = combinedLowerText(candidate);

        double importance = ingredientImportanceScore(candidate, ingredients, mentioned);
        double intentAlignment = intentAlignmentScore(preferenceTags, categories, combinedText, candidate);
        double popularity = popularityHeuristicScore(candidate);
        double reliability = sourceReliabilityScore(candidate);

        double total = WEIGHT_INTENT_ALIGNMENT * intentAlignment
                + WEIGHT_POPULARITY * popularity
                + WEIGHT_SOURCE * reliability;

        if (!mentioned.isEmpty()) {
            double ingredientMatch = ingredientMatchScore(mentioned, matchedCount);
            double pantryUtilization = pantryUtilizationScore(ingredients, matchedCount);
            double missingPenalty = missingPenaltyScore(ingredients, missing.size());
            double titleRelevance = titleRelevanceScore(candidate, mentioned);
            double exactMatch = exactMatchScore(mentioned, synonymResolved, ingredients);
            double coverageBonus = coverageBonusScore(mentioned, matchedCount);

            total += WEIGHT_INGREDIENT_MATCH * ingredientMatch
                    + WEIGHT_PANTRY_UTILIZATION * pantryUtilization
                    + WEIGHT_INGREDIENT_IMPORTANCE * importance
                    + WEIGHT_TITLE_RELEVANCE * titleRelevance
                    + WEIGHT_EXACT_MATCH * exactMatch
                    + WEIGHT_COVERAGE_BONUS * coverageBonus
                    - WEIGHT_MISSING_PENALTY * missingPenalty;
        } else {
            // No ingredient signal (a pure intent/browse request) - redistribute
            // the ingredient-signal budget into popularity+reliability so
            // "healthy recipes" or a blank browse still differentiates
            // candidates sensibly instead of leaving that weight unused.
            total += INGREDIENT_SIGNAL_BUDGET * ((popularity + reliability) / 2.0);
        }

        List<String> explanations = buildExplanations(candidate, mentioned, matchedCount, missing.size(),
                pantryUtilizationScore(ingredients, matchedCount), importance, preferenceTags, categories, combinedText);

        // Phase 7 - negation is applied last and unconditionally (both ingredient-signal
        // branches above), and never suppressed by anything else in the plan.
        double exclusionPenalty = exclusionPenaltyScore(combinedText, plan.getExcludedIngredients());
        if (exclusionPenalty > 0.0) {
            total -= WEIGHT_EXCLUSION * exclusionPenalty;
            explanations.add("Contains something you asked to avoid - ranked lower for that reason.");
        }

        return new RecipeScore(candidate, total, matchedCount, missing, explanations);
    }

    /**
     * How strongly this candidate conflicts with what the user explicitly said they don't
     * want, in [0, 1]. A single hit is already a strong conflict signal; additional excluded
     * terms found push it further toward the cap rather than linearly forever.
     */
    private double exclusionPenaltyScore(String combinedText, List<String> excludedIngredients) {
        if (excludedIngredients == null || excludedIngredients.isEmpty()) {
            return 0.0;
        }
        long hits = excludedIngredients.stream().filter(term -> matchesExcludedTerm(combinedText, term)).count();
        return hits == 0 ? 0.0 : Math.min(1.0, 0.7 + 0.15 * (hits - 1));
    }

    /**
     * A literal excluded ingredient ("mushrooms") is checked as a whole word, same as ever.
     * An excluded trait word that maps to a broader keyword vocabulary elsewhere in this engine
     * ("spicy") is checked against that whole vocabulary instead, since the literal word is
     * rarely what actually appears in a recipe's title or ingredient list.
     */
    private boolean matchesExcludedTerm(String combinedText, String term) {
        Set<String> traitKeywords = EXCLUDABLE_TRAIT_KEYWORDS.get(term);
        if (traitKeywords != null) {
            return containsAny(combinedText, traitKeywords);
        }
        return containsAsWord(combinedText, term);
    }

    // ---------------------------------------------------------------- Phase 6A: personalization

    /**
     * Backward-compatible overload: identical to {@link #score(RecipeCandidate, RetrievalPlan)}
     * when {@code profile} is null/empty, and additive otherwise - stored preferences and Smart
     * Variety only ever nudge the deterministic base score computed above, never replace it, and
     * the current request's own signals (ingredients, intent tags) always dominate.
     */
    public RecipeScore score(RecipeCandidate candidate, RetrievalPlan plan, UserProfileSnapshot profile) {
        RecipeScore base = score(candidate, plan);
        if (profile == null || profile.isEmpty()) {
            return base;
        }

        double personalization = personalizationScore(candidate, profile);
        double varietyPenalty = smartVarietyPenalty(candidate, profile);
        double dietaryPenalty = dietaryRestrictionPenalty(candidate, profile);
        double total = base.total() + WEIGHT_PERSONALIZATION * personalization
                - WEIGHT_SMART_VARIETY * varietyPenalty
                - WEIGHT_DIETARY_RESTRICTION * dietaryPenalty;

        List<String> explanations = new ArrayList<>(base.explanations());
        addPersonalizationExplanations(explanations, candidate, profile);
        if (dietaryPenalty > 0) {
            explanations.add("Heads up: this may not fit a dietary restriction on your profile.");
        }

        return new RecipeScore(candidate, total, base.matchedCount(), base.missingIngredients(), explanations);
    }

    /**
     * Phase 6B: identical to {@link #score(RecipeCandidate, RetrievalPlan, UserProfileSnapshot)}
     * when {@code pantry} is null/empty, and additive otherwise - pantry coverage and expiry
     * usage only ever nudge the score, never override the current request's own ingredient/
     * intent signals, which stay dominant via the much larger ingredient-match weights above.
     */
    public RecipeScore score(RecipeCandidate candidate, RetrievalPlan plan, UserProfileSnapshot profile,
                              PantrySnapshot pantry) {
        RecipeScore base = score(candidate, plan, profile);
        if (pantry == null || pantry.isEmpty() || candidate.getIngredients() == null) {
            return base;
        }

        List<String> pantryNames = pantry.ingredientNames();
        String combinedText = combinedLowerText(candidate);

        long ownedCount = candidate.getIngredients().stream()
                .filter(line -> pantryNames.stream().anyMatch(p -> containsAsWord(line.toLowerCase(Locale.ROOT), p)))
                .count();
        double coverage = candidate.getIngredients().isEmpty() ? 0.0
                : (double) ownedCount / candidate.getIngredients().size();

        List<PantrySnapshot.Item> expiringUsed = pantry.getItems().stream()
                .filter(PantrySnapshot.Item::isExpiringSoon)
                .filter(item -> containsAsWord(combinedText, item.getIngredientName()))
                .toList();

        double total = base.total() + WEIGHT_PANTRY_COVERAGE * coverage
                + (expiringUsed.isEmpty() ? 0.0 : WEIGHT_EXPIRY_USAGE);

        List<String> explanations = new ArrayList<>(base.explanations());
        if (!expiringUsed.isEmpty()) {
            String names = expiringUsed.stream().map(PantrySnapshot.Item::getIngredientName)
                    .collect(Collectors.joining(", "));
            explanations.add("Uses " + names + " before it expires.");
        } else if (coverage >= 0.7) {
            explanations.add("You already have most of what this needs.");
        }

        return new RecipeScore(candidate, total, base.matchedCount(), base.missingIngredients(), explanations);
    }

    /**
     * Averages every stored preference's contribution for this candidate into a single
     * [-1, 1] signal: favorite ingredients/cuisines the candidate contains push it up
     * (weighted by how confident that preference is), disliked ingredients and dietary
     * conflicts push it down. A preference the candidate has no relation to contributes 0,
     * not a penalty - absence of evidence isn't evidence of a mismatch.
     */
    private double personalizationScore(RecipeCandidate candidate, UserProfileSnapshot profile) {
        String combinedText = combinedLowerText(candidate);
        List<Double> contributions = new ArrayList<>();

        for (UserProfileSnapshot.PreferenceSignal pref : profile.getPreferences()) {
            switch (pref.getCategory()) {
                case FAVORITE_INGREDIENT, FAVORITE_CUISINE, FAVORITE_MEAL_CATEGORY, FAVORITE_COOKING_METHOD -> {
                    if (containsAsWord(combinedText, pref.getValue())) {
                        contributions.add(pref.getConfidence());
                    }
                }
                case DISLIKED_INGREDIENT -> {
                    if (containsAsWord(combinedText, pref.getValue())) {
                        contributions.add(-pref.getConfidence());
                    }
                }
                case DIETARY_RESTRICTION -> {
                    // Handled separately in dietaryRestrictionPenalty at hard-exclusion scale,
                    // not blended into this soft average - see WEIGHT_DIETARY_RESTRICTION.
                }
                case HEALTH_GOAL -> {
                    Double alignment = healthGoalAlignment(pref.getValue(), combinedText);
                    if (alignment != null) {
                        contributions.add(pref.getConfidence() * alignment);
                    }
                }
                default -> {
                    // COOKING_SKILL / PREFERRED_COOKING_DURATION / PREFERRED_SERVING_SIZE /
                    // FAVORITE_APPLIANCE aren't derivable from candidate text alone with what
                    // RecipeCandidate carries today - left for a future refinement rather than
                    // guessed at.
                }
            }
        }

        if (contributions.isEmpty()) {
            return 0.0;
        }
        double avg = contributions.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        return Math.max(-1.0, Math.min(1.0, avg));
    }

    /**
     * Standing dietary restrictions (Ω-2 Part 16: safety-relevant, not a taste nudge) apply at
     * WEIGHT_DIETARY_RESTRICTION scale, deliberately kept outside {@link #personalizationScore}'s
     * blended average so a strong ingredient/title match can never casually outrank a known
     * restriction the way a soft favorite/dislike signal can. Takes the single worst (highest
     * confidence x conflict) restriction rather than summing multiple, since this is a penalty
     * scale, not a preference-strength average - one real conflict is already disqualifying
     * regardless of how many restrictions happen to be on file.
     */
    private double dietaryRestrictionPenalty(RecipeCandidate candidate, UserProfileSnapshot profile) {
        String combinedText = combinedLowerText(candidate);
        double worst = 0.0;
        for (UserProfileSnapshot.PreferenceSignal pref : profile.getPreferences()) {
            if (pref.getCategory() != PreferenceCategory.DIETARY_RESTRICTION) {
                continue;
            }
            Double conflict = dietaryConflictPenalty(pref.getValue(), combinedText);
            if (conflict != null) {
                worst = Math.max(worst, pref.getConfidence() * conflict);
            }
        }
        return worst;
    }

    /** Returns 1.0 (hard conflict), a partial value, or null (no conflict / not a recognized diet). */
    private Double dietaryConflictPenalty(String diet, String combinedText) {
        return switch (diet) {
            case "vegetarian" -> containsAny(combinedText, MEAT_FISH_KEYWORDS) ? 1.0 : null;
            case "vegan" -> (containsAny(combinedText, MEAT_FISH_KEYWORDS)
                    || containsAny(combinedText, ANIMAL_PRODUCT_KEYWORDS)) ? 1.0 : null;
            case "pescatarian" -> containsAny(combinedText, Set.of("chicken", "beef", "pork", "bacon", "sausage",
                    "ham", "lamb", "turkey")) ? 1.0 : null;
            default -> null; // gluten-free/dairy-free/keto/halal/kosher: not reliably derivable from free-text
                              // ingredient lines without a structured allergen model - deferred rather than guessed.
        };
    }

    /**
     * Phase 7 (Part 3) - lightweight, explainable health-goal alignment in [-1, 1] using
     * ingredient-keyword proxies (never a fabricated nutrient count - "Never hallucinate
     * nutrition" applies here too, even though this is ranking, not a Q&A answer). Grounded,
     * per-candidate USDA lookups for every scored candidate would be both slow (a live HTTP
     * call per candidate per ranking pass) and often unavailable (no API key configured) -
     * see {@code NutritionQuestionComposer} for where real USDA numbers are used instead, for
     * an explicit question about one specific recipe.
     */
    private Double healthGoalAlignment(String goal, String combinedText) {
        boolean vegetableOrLean = containsAny(combinedText, VEGETABLE_KEYWORDS)
                || containsAny(combinedText, LEAN_PROTEIN_KEYWORDS);
        boolean heavy = containsAny(combinedText, FRIED_OR_HEAVY_KEYWORDS);
        boolean sodiumHeavy = containsAny(combinedText, SODIUM_HEAVY_KEYWORDS);

        return switch (goal) {
            case "weight_loss" -> vegetableOrLean && !heavy ? 1.0 : heavy ? -0.6 : 0.0;
            case "weight_gain" -> containsAny(combinedText, HIGH_CARB_KEYWORDS)
                    && containsAny(combinedText, PROTEIN_KEYWORDS) ? 0.8 : 0.0;
            case "muscle_gain", "high_protein" -> containsAny(combinedText, PROTEIN_KEYWORDS) ? 0.9 : -0.2;
            case "low_sodium" -> sodiumHeavy ? -0.8 : 0.4;
            case "high_fiber" -> containsAny(combinedText, VEGETABLE_KEYWORDS)
                    || combinedText.contains("bean") || combinedText.contains("lentil")
                    || combinedText.contains("oat") || combinedText.contains("whole grain") ? 0.7 : 0.0;
            case "heart_healthy" -> vegetableOrLean && !sodiumHeavy && !heavy ? 0.8
                    : (sodiumHeavy || heavy) ? -0.5 : 0.0;
            case "general_healthy" -> vegetableOrLean ? 0.6 : heavy ? -0.4 : 0.0;
            default -> null; // unrecognized goal value - never guessed at
        };
    }

    /**
     * Smart Variety: a soft, purely additive penalty for recipes the user has been shown
     * recently, so the same handful of dishes don't dominate every recommendation. Never an
     * exclusion - {@code profile.getRecentRecipeTitles()} only informs ranking, and an
     * explicit request (the current message's own ingredients/intent tags) is unaffected by it.
     */
    private double smartVarietyPenalty(RecipeCandidate candidate, UserProfileSnapshot profile) {
        List<String> recent = profile.getRecentRecipeTitles();
        if (recent == null || recent.isEmpty() || candidate.getTitle() == null) {
            return 0.0;
        }
        String normalizedTitle = candidate.getTitle().trim().toLowerCase(Locale.ROOT);
        // recent titles come straight from RecipeHistoryEntry with their original casing/
        // whitespace, never normalized at the source - comparing them against a normalized
        // candidate title via indexOf() was always a case-sensitive exact match, so this
        // penalty silently never fired for any title that wasn't already all-lowercase.
        // Normalize this side the same way before comparing.
        int index = -1;
        for (int i = 0; i < recent.size(); i++) {
            String title = recent.get(i);
            if (title != null && title.trim().toLowerCase(Locale.ROOT).equals(normalizedTitle)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return 0.0;
        }
        if (index == 0) {
            return 1.0;
        }
        if (index <= 2) {
            return 0.6;
        }
        return 0.3;
    }

    private void addPersonalizationExplanations(List<String> explanations, RecipeCandidate candidate,
                                                 UserProfileSnapshot profile) {
        String combinedText = combinedLowerText(candidate);
        boolean addedFavorite = false;
        boolean addedHealthGoal = false;

        for (UserProfileSnapshot.PreferenceSignal pref : profile.getPreferences()) {
            if (pref.getConfidence() < 0.6) {
                continue;
            }
            boolean favorite = pref.getCategory() == PreferenceCategory.FAVORITE_INGREDIENT
                    || pref.getCategory() == PreferenceCategory.FAVORITE_CUISINE;
            if (!addedFavorite && favorite && containsAsWord(combinedText, pref.getValue())) {
                explanations.add("Matches something you like: " + pref.getValue() + ".");
                addedFavorite = true; // one favorite callout is plenty - avoid a wall of explanations
            }
            if (!addedHealthGoal && pref.getCategory() == PreferenceCategory.HEALTH_GOAL) {
                Double alignment = healthGoalAlignment(pref.getValue(), combinedText);
                if (alignment != null && alignment > 0.5) {
                    explanations.add("Fits your " + pref.getValue().replace('_', ' ') + " goal.");
                    addedHealthGoal = true;
                }
            }
        }
    }

    // ---------------------------------------------------------------- ingredient match

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

    // ---------------------------------------------------------------- pantry utilization

    private double pantryUtilizationScore(List<String> ingredients, int matchedCount) {
        if (ingredients.isEmpty()) {
            return 0.0;
        }
        return Math.min(1.0, matchedCount / (double) ingredients.size());
    }

    // ---------------------------------------------------------------- missing-ingredient penalty

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

    // ---------------------------------------------------------------- tiered ingredient importance

    /**
     * For each mentioned ingredient, determines whether it's the PRIMARY
     * identity of the dish (in the title), a SECONDARY ingredient (early
     * in the list but not titled), a MINOR one (late in the list), a
     * GARNISH ("to serve", "optional", ...), or ABSENT entirely - then
     * averages the tier weights across every mentioned ingredient. This is
     * what makes "eggs" favor an omelette (egg in the title = PRIMARY)
     * over a recipe where egg is one of many ("chicken stock" deep in a
     * long ingredient list = MINOR, not a real match for a "chicken"
     * search).
     */
    private double ingredientImportanceScore(RecipeCandidate candidate, List<String> ingredients, List<String> mentioned) {
        if (mentioned.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (String token : mentioned) {
            sum += importanceTier(candidate, ingredients, token).weight;
        }
        return sum / mentioned.size();
    }

    private ImportanceTier importanceTier(RecipeCandidate candidate, List<String> ingredients, String token) {
        String titleLower = candidate.getTitle() == null ? "" : candidate.getTitle().toLowerCase(Locale.ROOT);
        if (containsAsWord(titleLower, token)) {
            return ImportanceTier.PRIMARY;
        }

        int matchIndex = -1;
        for (int i = 0; i < ingredients.size(); i++) {
            if (containsAsWord(ingredients.get(i).toLowerCase(Locale.ROOT), token)) {
                matchIndex = i;
                break;
            }
        }
        if (matchIndex == -1) {
            return ImportanceTier.ABSENT;
        }

        String matchedLine = ingredients.get(matchIndex).toLowerCase(Locale.ROOT);
        if (GARNISH_INDICATORS.stream().anyMatch(matchedLine::contains)) {
            return ImportanceTier.GARNISH;
        }

        int size = ingredients.size();
        double positionFraction = size <= 1 ? 0.0 : matchIndex / (double) (size - 1);
        return positionFraction <= 0.34 ? ImportanceTier.SECONDARY : ImportanceTier.MINOR;
    }

    // ---------------------------------------------------------------- title relevance

    private double titleRelevanceScore(RecipeCandidate candidate, List<String> mentioned) {
        if (mentioned.isEmpty() || candidate.getTitle() == null) {
            return 0.0;
        }
        String titleLower = candidate.getTitle().toLowerCase(Locale.ROOT);
        long titleMatches = mentioned.stream().filter(m -> containsAsWord(titleLower, m)).count();
        return titleMatches / (double) mentioned.size();
    }

    // ---------------------------------------------------------------- exact vs synonym match

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

    // ---------------------------------------------------------------- multi-ingredient coverage

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

    // ---------------------------------------------------------------- intent alignment

    /**
     * How well this candidate fits the user's non-ingredient intent
     * (meal type, dietary preference, budget, occasion) - the signal that
     * lets "need dinner" prefer a real dinner over a dessert, and "healthy
     * recipes" prefer vegetables and lean protein over anything that
     * merely has the word "healthy" nearby. Returns a neutral 0.5 when
     * there's no preference tag at all, so it never skews a plain
     * ingredient search.
     */
    private double intentAlignmentScore(Set<String> preferenceTags, Set<Category> categories,
                                         String combinedText, RecipeCandidate candidate) {
        if (preferenceTags.isEmpty()) {
            return 0.5;
        }
        List<Double> scores = new ArrayList<>();
        addIfPresent(scores, mealTypeAlignment(preferenceTags, categories));
        addIfPresent(scores, dietaryAlignment(preferenceTags, combinedText, categories));
        addIfPresent(scores, budgetAlignment(preferenceTags, candidate));
        addIfPresent(scores, occasionAlignment(preferenceTags, categories, candidate));
        return scores.isEmpty() ? 0.5 : average(scores);
    }

    private Optional<Double> mealTypeAlignment(Set<String> tags, Set<Category> categories) {
        List<Double> scores = new ArrayList<>();
        if (tags.contains("breakfast")) {
            scores.add(categories.contains(Category.BREAKFAST) ? 1.0
                    : isNotAMeal(categories) ? 0.1 : 0.5);
        }
        if (tags.contains("lunch")) {
            boolean fits = categories.contains(Category.LUNCH) || isSubstantialMeal(categories);
            scores.add(fits ? 0.9 : isNotAMeal(categories) ? 0.1 : 0.5);
        }
        if (tags.contains("dinner") || tags.contains("general_meal")) {
            boolean substantial = categories.contains(Category.DINNER) || isSubstantialMeal(categories);
            boolean notAMeal = isNotAMeal(categories) || categories.contains(Category.SNACK);
            scores.add(substantial ? 1.0 : notAMeal ? 0.05 : 0.5);
        }
        if (tags.contains("dessert")) {
            scores.add(categories.contains(Category.DESSERT) ? 1.0 : 0.3);
        }
        if (tags.contains("snack")) {
            scores.add(categories.contains(Category.SNACK) ? 1.0 : isSubstantialMeal(categories) ? 0.3 : 0.5);
        }
        return scores.isEmpty() ? Optional.empty() : Optional.of(average(scores));
    }

    private Optional<Double> dietaryAlignment(Set<String> tags, String text, Set<Category> categories) {
        List<Double> scores = new ArrayList<>();
        if (tags.contains("healthy")) {
            boolean wholesome = containsAny(text, VEGETABLE_KEYWORDS) || containsAny(text, LEAN_PROTEIN_KEYWORDS);
            scores.add(categories.contains(Category.DESSERT) ? 0.1 : wholesome ? 0.9 : 0.4);
        }
        if (tags.contains("high_protein")) {
            scores.add(containsAny(text, PROTEIN_KEYWORDS) ? 0.9 : 0.3);
        }
        if (tags.contains("vegetarian")) {
            scores.add(containsAny(text, MEAT_FISH_KEYWORDS) ? 0.05 : 0.9);
        }
        if (tags.contains("vegan")) {
            boolean hasAnimalProduct = containsAny(text, MEAT_FISH_KEYWORDS) || containsAny(text, ANIMAL_PRODUCT_KEYWORDS);
            scores.add(hasAnimalProduct ? 0.05 : 0.9);
        }
        if (tags.contains("low_carb")) {
            // Whole-word matching matters here: plain substring matching made "flour" a false
            // positive inside "cauliflower", incorrectly flagging a classic low-carb vegetable
            // as carb-heavy.
            long carbHits = HIGH_CARB_KEYWORDS.stream().filter(k -> containsAsWord(text, k)).count();
            scores.add(carbHits >= 2 ? 0.1 : carbHits == 1 ? 0.4 : 0.9);
        }
        if (tags.contains("spicy")) {
            scores.add(containsAny(text, SPICY_KEYWORDS) ? 0.9 : 0.3);
        }
        return scores.isEmpty() ? Optional.empty() : Optional.of(average(scores));
    }

    private Optional<Double> budgetAlignment(Set<String> tags, RecipeCandidate candidate) {
        if (!tags.contains("cheap")) {
            return Optional.empty();
        }
        List<String> ingredients = candidate.getIngredients() == null ? List.of() : candidate.getIngredients();
        if (ingredients.isEmpty()) {
            return Optional.of(0.5);
        }
        double commonFraction = commonIngredientFraction(ingredients);
        boolean smallList = ingredients.size() <= 8;
        double score = commonFraction >= 0.6 && smallList ? 0.9 : commonFraction >= 0.6 ? 0.6 : 0.3;
        return Optional.of(score);
    }

    private Optional<Double> occasionAlignment(Set<String> tags, Set<Category> categories, RecipeCandidate candidate) {
        List<Double> scores = new ArrayList<>();
        int ingredientCount = candidate.getIngredients() == null ? 0 : candidate.getIngredients().size();
        int stepCount = candidate.getSteps() == null ? 0 : candidate.getSteps().size();

        if (tags.contains("quick") || tags.contains("easy")) {
            boolean simple = ingredientCount > 0 && ingredientCount <= 8 && stepCount > 0 && stepCount <= 6;
            scores.add(simple ? 0.9 : 0.4);
        }
        if (tags.contains("comfort_food")) {
            boolean comforting = categories.contains(Category.MAIN_COURSE) || categories.contains(Category.SOUP)
                    || categories.contains(Category.PASTA) || categories.contains(Category.BREAD);
            scores.add(comforting ? 0.85 : 0.5);
        }
        if (tags.contains("family")) {
            scores.add(isSubstantialMeal(categories) ? 0.8 : 0.5);
        }
        if (tags.contains("date_night")) {
            boolean special = categories.contains(Category.MAIN_COURSE) || categories.contains(Category.DESSERT);
            scores.add(special ? 0.7 : 0.5);
        }
        // Phase 7 - meal_prep favors dishes that reheat/keep well (soups, rice/pasta bakes,
        // stews) over delicate or assembly-at-the-table dishes (salads, anything fried-to-order).
        if (tags.contains("meal_prep")) {
            boolean reheatsWell = categories.contains(Category.SOUP) || categories.contains(Category.RICE_DISH)
                    || categories.contains(Category.MAIN_COURSE) || categories.contains(Category.PASTA);
            boolean fragile = categories.contains(Category.SALAD);
            scores.add(reheatsWell && !fragile ? 0.85 : fragile ? 0.3 : 0.5);
        }
        // movie_night favors easy, hand-held, snackable food over a formal sit-down main course.
        if (tags.contains("movie_night")) {
            boolean snackable = categories.contains(Category.SNACK) || categories.contains(Category.DIP)
                    || categories.contains(Category.SIDE_DISH);
            boolean formalMain = categories.contains(Category.MAIN_COURSE) && ingredientCount > 10;
            scores.add(snackable ? 0.85 : formalMain ? 0.3 : 0.55);
        }
        return scores.isEmpty() ? Optional.empty() : Optional.of(average(scores));
    }

    private boolean isSubstantialMeal(Set<Category> categories) {
        return categories.contains(Category.MAIN_COURSE) || categories.contains(Category.SOUP)
                || categories.contains(Category.PASTA) || categories.contains(Category.RICE_DISH)
                || categories.contains(Category.DINNER) || categories.contains(Category.SALAD);
    }

    private boolean isNotAMeal(Set<Category> categories) {
        return categories.stream().anyMatch(RecipeCategoryClassifier.NOT_A_MEAL::contains);
    }

    // ---------------------------------------------------------------- popularity heuristic

    /**
     * Estimates how "trustworthy"/mainstream a recipe looks using only
     * metadata we actually have - no fabricated rating or prep-time. A
     * well-formed title, a reasonable ingredient count, common (not
     * obscure) ingredients, and a complete write-up are all real,
     * defensible proxies for quality even without explicit ratings.
     */
    private double popularityHeuristicScore(RecipeCandidate candidate) {
        double completeness = completenessScore(candidate);
        double titleQuality = titleQualityScore(candidate.getTitle());
        double ingredientCountReasonableness = ingredientCountReasonablenessScore(candidate);
        List<String> ingredients = candidate.getIngredients() == null ? List.of() : candidate.getIngredients();
        double commonIngredientFraction = ingredients.isEmpty() ? 0.3 : Math.min(1.0, commonIngredientFraction(ingredients) * 1.5);
        return (completeness + titleQuality + ingredientCountReasonableness + commonIngredientFraction) / 4.0;
    }

    private double titleQualityScore(String title) {
        if (title == null || title.isBlank()) {
            return 0.0;
        }
        String trimmed = title.trim();
        int wordCount = trimmed.split("\\s+").length;
        boolean reasonableLength = wordCount >= 2 && wordCount <= 12;
        boolean notShouting = !(trimmed.length() > 3 && trimmed.equals(trimmed.toUpperCase(Locale.ROOT))
                && !trimmed.equals(trimmed.toLowerCase(Locale.ROOT)));
        boolean notExcessivePunctuation = trimmed.chars().filter(c -> c == '!' || c == '?').count() <= 1;

        int points = 0;
        if (reasonableLength) {
            points++;
        }
        if (notShouting) {
            points++;
        }
        if (notExcessivePunctuation) {
            points++;
        }
        return points / 3.0;
    }

    private double ingredientCountReasonablenessScore(RecipeCandidate candidate) {
        int count = candidate.getIngredients() == null ? 0 : candidate.getIngredients().size();
        if (count < 2) {
            return 0.2;
        }
        if (count <= 20) {
            return 1.0;
        }
        return 0.5;
    }

    private double commonIngredientFraction(List<String> ingredients) {
        if (ingredients.isEmpty()) {
            return 0.0;
        }
        long common = ingredients.stream().filter(this::isCommonIngredientLine).count();
        return common / (double) ingredients.size();
    }

    private boolean isCommonIngredientLine(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return TypoCorrectionUtil.VOCABULARY.stream().anyMatch(v -> containsAsWord(lower, v));
    }

    // ---------------------------------------------------------------- completeness

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

    // ---------------------------------------------------------------- source reliability

    private double sourceReliabilityScore(RecipeCandidate candidate) {
        String source = candidate.getSource() == null ? "" : candidate.getSource();
        return SOURCE_RELIABILITY.getOrDefault(source, 0.4);
    }

    // ---------------------------------------------------------------- explanations

    private List<String> buildExplanations(RecipeCandidate candidate, List<String> mentioned, int matchedCount,
                                            int missingCount, double pantryUtilization, double importance,
                                            Set<String> preferenceTags, Set<Category> categories, String combinedText) {
        List<String> explanations = new ArrayList<>();

        if (!mentioned.isEmpty()) {
            if (matchedCount == mentioned.size() && mentioned.size() > 1) {
                explanations.add("Uses all " + mentioned.size() + " ingredients you mentioned.");
            } else if (matchedCount == mentioned.size() && mentioned.size() == 1) {
                explanations.add(capitalize(mentioned.get(0)) + " is the primary ingredient.");
            } else if (mentioned.size() > 1 && matchedCount / (double) mentioned.size() >= 0.6) {
                explanations.add("High ingredient match.");
            } else if (matchedCount > 0) {
                explanations.add("Uses " + matchedCount + " of the " + mentioned.size() + " ingredient(s) you mentioned.");
            }
            if (matchedCount > 0 && missingCount <= 2) {
                explanations.add(missingCount == 0 ? "Nothing else to buy." : "Only " + missingCount + " ingredient(s) missing.");
            }
            if (pantryUtilization >= 0.5) {
                explanations.add("Excellent pantry utilization.");
            }
            if (importance >= 0.9 && mentioned.size() > 1) {
                explanations.add("Your ingredients are central to this dish, not an afterthought.");
            }
        }

        addIntentExplanations(explanations, preferenceTags, categories, combinedText, candidate);

        return explanations;
    }

    private void addIntentExplanations(List<String> explanations, Set<String> preferenceTags,
                                        Set<Category> categories, String combinedText, RecipeCandidate candidate) {
        if (preferenceTags.contains("dinner") || preferenceTags.contains("general_meal")) {
            if (isSubstantialMeal(categories)) {
                explanations.add("Great dinner option.");
            }
        }
        if (preferenceTags.contains("breakfast") && categories.contains(Category.BREAKFAST)) {
            int ingredientCount = candidate.getIngredients() == null ? 0 : candidate.getIngredients().size();
            explanations.add(ingredientCount > 0 && ingredientCount <= 6 ? "Quick breakfast." : "Good breakfast option.");
        }
        if (preferenceTags.contains("healthy")
                && (containsAny(combinedText, VEGETABLE_KEYWORDS) || containsAny(combinedText, LEAN_PROTEIN_KEYWORDS))
                && !categories.contains(Category.DESSERT)) {
            explanations.add("Great healthy choice.");
        }
        if (preferenceTags.contains("high_protein") && containsAny(combinedText, PROTEIN_KEYWORDS)) {
            explanations.add("High protein option.");
        }
        if (preferenceTags.contains("vegetarian") && !containsAny(combinedText, MEAT_FISH_KEYWORDS)) {
            explanations.add("Vegetarian-friendly.");
        }
        if (preferenceTags.contains("vegan")
                && !containsAny(combinedText, MEAT_FISH_KEYWORDS) && !containsAny(combinedText, ANIMAL_PRODUCT_KEYWORDS)) {
            explanations.add("Vegan-friendly.");
        }
        if (preferenceTags.contains("cheap") && commonIngredientFraction(
                candidate.getIngredients() == null ? List.of() : candidate.getIngredients()) >= 0.6) {
            explanations.add("Budget-friendly ingredients.");
        }
        if ((preferenceTags.contains("quick") || preferenceTags.contains("easy"))
                && candidate.getSteps() != null && candidate.getSteps().size() <= 6
                && candidate.getIngredients() != null && candidate.getIngredients().size() <= 8) {
            explanations.add("Quick and easy.");
        }
        if (preferenceTags.contains("comfort_food")
                && (categories.contains(Category.MAIN_COURSE) || categories.contains(Category.SOUP)
                    || categories.contains(Category.PASTA) || categories.contains(Category.BREAD))) {
            explanations.add("Cozy comfort food.");
        }
        if (preferenceTags.contains("meal_prep")
                && (categories.contains(Category.SOUP) || categories.contains(Category.RICE_DISH)
                    || categories.contains(Category.MAIN_COURSE) || categories.contains(Category.PASTA))) {
            explanations.add("Reheats well for meal prep.");
        }
        if (preferenceTags.contains("movie_night")
                && (categories.contains(Category.SNACK) || categories.contains(Category.DIP))) {
            explanations.add("Easy, hand-held movie-night food.");
        }
    }

    // ---------------------------------------------------------------- diversity / duplicate handling

    /**
     * Walks the score-sorted candidates and skips ones whose title (plus
     * inferred primary category) is a near-duplicate of one already
     * selected, so a results page isn't five nearly-identical variants of
     * the same dish. Backfills from skipped (duplicate) candidates, still
     * in score order, if there aren't enough distinct ones to fill the
     * page - diversity should never mean returning fewer results than
     * requested when more exist.
     */
    public List<RecipeScore> selectDiverseTopResults(List<RecipeScore> scoredDescending, int maxResults) {
        List<RecipeScore> selected = new ArrayList<>();
        List<RecipeScore> skippedDuplicates = new ArrayList<>();
        List<Set<String>> selectedSignatures = new ArrayList<>();

        for (RecipeScore candidate : scoredDescending) {
            if (selected.size() >= maxResults) {
                break;
            }
            Set<String> signature = titleSignature(candidate.candidate());
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

    private Set<String> titleSignature(RecipeCandidate candidate) {
        String title = candidate.getTitle();
        Set<String> signature = new LinkedHashSet<>();
        if (title != null) {
            String normalized = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ");
            for (String word : normalized.split("\\s+")) {
                if (word.length() >= 3 && !TITLE_NOISE_WORDS.contains(word)) {
                    signature.add(word);
                }
            }
        }
        categoryClassifier.primaryCategory(candidate).ifPresent(category -> signature.add("cat:" + category));
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

    private String combinedLowerText(RecipeCandidate candidate) {
        String title = candidate.getTitle() == null ? "" : candidate.getTitle();
        String ingredients = candidate.getIngredients() == null ? "" : String.join(" ", candidate.getIngredients());
        return (title + " " + ingredients).toLowerCase(Locale.ROOT);
    }

    /**
     * Whole-word/whole-phrase containment for every keyword, delegating to
     * {@link #containsAsWord} - fixes false positives from the previous plain
     * substring check (e.g. "egg" matching inside "eggplant", "oil" matching
     * inside "boil"/"foil", "heat" matching inside "wheat", "salted" matching
     * inside "unsalted"), which was silently corrupting dietary, exclusion,
     * health-goal, and popularity signals across the engine.
     */
    private boolean containsAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (containsAsWord(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    private void addIfPresent(List<Double> scores, Optional<Double> value) {
        value.ifPresent(scores::add);
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
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
