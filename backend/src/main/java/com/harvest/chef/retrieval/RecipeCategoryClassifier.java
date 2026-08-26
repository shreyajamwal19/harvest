package com.harvest.chef.retrieval;

import com.harvest.chef.dto.RecipeCandidate;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic dish-category inference from a recipe's title and
 * ingredient list - no ML, just curated keyword sets per category. Titles
 * are the primary signal (far more reliable than ingredients: "Egg Fried
 * Rice" tells you immediately it's a rice dish; a shared ingredient like
 * "egg" tells you almost nothing about dish type on its own), with
 * ingredient text as a lower-weighted fallback signal.
 *
 * A recipe can plausibly belong to more than one category (a curry can be
 * both MAIN_COURSE and, say, DINNER-appropriate) - {@link #classify}
 * returns every category that matched, letting callers pick a primary one
 * or reason about the full set.
 */
@Component
public class RecipeCategoryClassifier {

    public enum Category {
        BREAKFAST, LUNCH, DINNER, DESSERT, SNACK, DRINK,
        SAUCE, DIP, SIDE_DISH, MAIN_COURSE, SOUP, SALAD,
        PASTA, RICE_DISH, BREAD
    }

    // Title keywords are the strongest signal - checked first and weighted
    // higher than ingredient-text keywords in the caller's scoring.
    private static final Map<Category, Set<String>> TITLE_KEYWORDS = new EnumMap<>(Category.class);
    private static final Map<Category, Set<String>> INGREDIENT_KEYWORDS = new EnumMap<>(Category.class);

    // Categories that are fundamentally at odds with "this is a real dinner
    // meal" - used by the intent-alignment scoring in RecipeScoringEngine to
    // penalize e.g. a sauce or dessert showing up for "need dinner".
    public static final Set<Category> NOT_A_MEAL = Set.of(Category.SAUCE, Category.DIP, Category.DESSERT);

    static {
        TITLE_KEYWORDS.put(Category.BREAKFAST, Set.of(
                "breakfast", "omelette", "omelet", "pancake", "pancakes", "waffle", "waffles",
                "scrambled", "frittata", "granola", "oatmeal", "porridge", "brunch", "hash browns",
                "french toast", "cereal", "bagel"));
        TITLE_KEYWORDS.put(Category.LUNCH, Set.of("lunch", "sandwich", "wrap", "panini", "bowl"));
        TITLE_KEYWORDS.put(Category.DINNER, Set.of("dinner", "supper"));
        // Deliberately NOT including bare "sweet": it's a whole word in plenty of savory dish
        // names ("Sweet Potato Casserole", "Sweet and Sour Chicken", "Sweet Corn Fritters",
        // "Candied Sweet Potatoes"), and word-boundary matching alone doesn't disambiguate that
        // - the word itself is just genuinely ambiguous between "a sweet flavor" and "a dessert".
        // Multi-word dessert-specific phrases are unambiguous and stay.
        TITLE_KEYWORDS.put(Category.DESSERT, Set.of(
                "cake", "cookie", "cookies", "brownie", "brownies", "pie", "tart", "pudding",
                "ice cream", "cheesecake", "dessert", "candy", "fudge", "cupcake", "muffin",
                "chocolate chip", "sweet treat", "sweet tooth"));
        TITLE_KEYWORDS.put(Category.SNACK, Set.of("snack", "bites", "chips", "popcorn", "trail mix"));
        TITLE_KEYWORDS.put(Category.DRINK, Set.of(
                "smoothie", "shake", "cocktail", "lemonade", "juice", "punch", "latte", "tea",
                "coffee", "mocktail"));
        TITLE_KEYWORDS.put(Category.SAUCE, Set.of("sauce", "gravy", "salsa", "chutney", "marinade", "glaze"));
        TITLE_KEYWORDS.put(Category.DIP, Set.of("dip", "hummus", "guacamole", "spread"));
        TITLE_KEYWORDS.put(Category.SIDE_DISH, Set.of("side dish", "side", "coleslaw"));
        TITLE_KEYWORDS.put(Category.MAIN_COURSE, Set.of(
                "casserole", "roast", "curry", "stew", "stir fry", "stir-fry", "skillet", "bake",
                "meatloaf", "meatballs", "shakshuka", "tagine", "pot pie"));
        TITLE_KEYWORDS.put(Category.SOUP, Set.of("soup", "chowder", "bisque", "broth", "chili"));
        TITLE_KEYWORDS.put(Category.SALAD, Set.of("salad", "slaw"));
        TITLE_KEYWORDS.put(Category.PASTA, Set.of(
                "pasta", "spaghetti", "noodle", "noodles", "lasagna", "macaroni", "penne",
                "fettuccine", "ravioli", "linguine"));
        TITLE_KEYWORDS.put(Category.RICE_DISH, Set.of("rice", "risotto", "pilaf", "paella", "biryani", "fried rice"));
        // Deliberately NOT including bare "roll"/"rolls": the same ambiguity problem "sweet"
        // has above - "roll" is a whole word in plenty of non-bread dishes (spring rolls, egg
        // rolls, sushi rolls, cabbage rolls, lettuce rolls), so word-boundary matching alone
        // would mistag them as BREAD. Bread-specific roll phrasing is unambiguous and kept.
        TITLE_KEYWORDS.put(Category.BREAD, Set.of(
                "bread", "loaf", "biscuit", "biscuits", "focaccia", "naan", "dinner roll",
                "dinner rolls", "bread roll", "bread rolls", "cinnamon roll", "cinnamon rolls"));

        INGREDIENT_KEYWORDS.put(Category.DESSERT, Set.of("sugar", "powdered sugar", "chocolate chips", "frosting"));
        INGREDIENT_KEYWORDS.put(Category.PASTA, Set.of("pasta", "spaghetti", "noodles", "macaroni"));
        INGREDIENT_KEYWORDS.put(Category.RICE_DISH, Set.of("rice"));
        INGREDIENT_KEYWORDS.put(Category.SOUP, Set.of("broth", "stock"));
        INGREDIENT_KEYWORDS.put(Category.BREAD, Set.of("yeast", "flour"));
    }

    /** Every category whose keywords matched, in the fixed enum declaration order. */
    public Set<Category> classify(RecipeCandidate candidate) {
        String titleLower = candidate.getTitle() == null ? "" : candidate.getTitle().toLowerCase(Locale.ROOT);
        String ingredientsLower = candidate.getIngredients() == null
                ? "" : String.join(" ", candidate.getIngredients()).toLowerCase(Locale.ROOT);

        Set<Category> matched = new LinkedHashSet<>();
        mapMealCategory(candidate.getMealCategory()).ifPresent(matched::add);
        for (Category category : Category.values()) {
            if (matchesAny(titleLower, TITLE_KEYWORDS.getOrDefault(category, Set.of()))
                    || matchesAny(ingredientsLower, INGREDIENT_KEYWORDS.getOrDefault(category, Set.of()))) {
                matched.add(category);
            }
        }
        return matched;
    }

    /** The single strongest category, or empty if nothing matched - title matches always outrank ingredient-only ones. */
    public Optional<Category> primaryCategory(RecipeCandidate candidate) {
        // TheMealDB's own strCategory (when present) is real provider-supplied ground truth,
        // not a keyword guess from the title - stronger than any TITLE_KEYWORDS match, so it's
        // checked first rather than only as a tiebreaker buried inside classify().
        Optional<Category> fromProvider = mapMealCategory(candidate.getMealCategory());
        if (fromProvider.isPresent()) {
            return fromProvider;
        }
        String titleLower = candidate.getTitle() == null ? "" : candidate.getTitle().toLowerCase(Locale.ROOT);
        for (Category category : Category.values()) {
            if (matchesAny(titleLower, TITLE_KEYWORDS.getOrDefault(category, Set.of()))) {
                return Optional.of(category);
            }
        }
        Set<Category> all = classify(candidate);
        return all.stream().findFirst();
    }

    /**
     * Maps TheMealDB's strCategory to our Category enum, but only where the two concepts are
     * genuinely the same thing - "Dessert"/"Breakfast"/"Side"/"Pasta" translate directly.
     * Deliberately NOT mapped: "Starter" (could be soup, salad, or a dip - guessing which one
     * would be worse than no signal), and protein/diet groupings like "Beef"/"Chicken"/"Lamb"/
     * "Pork"/"Goat"/"Seafood"/"Vegetarian"/"Vegan"/"Miscellaneous" - those describe an
     * ingredient or diet, not a meal-type category, so forcing them into this enum would be a
     * category error, not a genuine classification.
     */
    private Optional<Category> mapMealCategory(String mealCategory) {
        if (mealCategory == null || mealCategory.isBlank()) {
            return Optional.empty();
        }
        return switch (mealCategory.trim().toLowerCase(Locale.ROOT)) {
            case "dessert" -> Optional.of(Category.DESSERT);
            case "breakfast" -> Optional.of(Category.BREAKFAST);
            case "side" -> Optional.of(Category.SIDE_DISH);
            case "pasta" -> Optional.of(Category.PASTA);
            default -> Optional.empty();
        };
    }

    private boolean matchesAny(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (containsAsWord(text, keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whole-word containment rather than plain substring matching. Without this, a single-word
     * keyword like "sweet" (for DESSERT) spuriously matches "Sweet Potato Casserole" or "Sweet
     * and Sour Chicken" - legitimate savory dinner mains - just because "sweet" is a substring
     * of the title. That false DESSERT tag then flags them as NOT_A_MEAL, which actively
     * suppresses genuinely good dinner options from a "healthy dinner" search for no reason a
     * user could ever guess. Multi-word phrases ("side dish", "stir fry") fall back to plain
     * substring matching, since word-boundary regex doesn't extend naturally across an embedded
     * space - the same tradeoff RecipeScoringEngine's own containsAsWord already makes.
     */
    private boolean containsAsWord(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        if (needle.contains(" ")) {
            return haystack.contains(needle);
        }
        return Pattern.compile("\\b" + Pattern.quote(needle) + "\\b").matcher(haystack).find();
    }
}
