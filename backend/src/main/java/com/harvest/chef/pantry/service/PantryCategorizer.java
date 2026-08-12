package com.harvest.chef.pantry.service;

import com.harvest.chef.pantry.entity.PantryCategory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministic ingredient -> {@link PantryCategory} inference, in the
 * same spirit as {@code RecipeCategoryClassifier} (curated keyword sets,
 * no ML). Used both for pantry display grouping and shopping-list
 * category headers, so both features stay consistent with each other for
 * free.
 */
@Component
public class PantryCategorizer {

    private static final Map<PantryCategory, Set<String>> KEYWORDS = new EnumMap<>(PantryCategory.class);

    static {
        KEYWORDS.put(PantryCategory.VEGETABLE, Set.of(
                "onion", "garlic", "tomato", "potato", "carrot", "pepper", "capsicum", "spinach",
                "lettuce", "cabbage", "broccoli", "cauliflower", "cucumber", "zucchini", "eggplant",
                "aubergine", "mushroom", "celery", "corn", "peas", "beans", "ginger", "chili", "chilli",
                "kale", "beet", "radish", "squash", "pumpkin", "asparagus", "leek", "scallion"));
        KEYWORDS.put(PantryCategory.FRUIT, Set.of(
                "apple", "banana", "orange", "lemon", "lime", "berry", "berries", "grape", "mango",
                "pineapple", "peach", "pear", "melon", "watermelon", "avocado", "cherry", "plum",
                "kiwi", "fig", "date", "coconut", "pomegranate"));
        KEYWORDS.put(PantryCategory.PROTEIN, Set.of(
                "chicken", "beef", "pork", "lamb", "turkey", "bacon", "sausage", "ham", "fish",
                "salmon", "tuna", "shrimp", "prawn", "egg", "eggs", "tofu", "tempeh", "paneer",
                "lentil", "lentils", "chickpea", "chickpeas"));
        KEYWORDS.put(PantryCategory.DAIRY, Set.of(
                "milk", "cheese", "butter", "cream", "yogurt", "yoghurt", "ghee", "sour cream",
                "buttermilk", "mozzarella", "cheddar", "parmesan"));
        KEYWORDS.put(PantryCategory.FROZEN, Set.of(
                "frozen", "ice cream", "popsicle"));
        KEYWORDS.put(PantryCategory.SPICE, Set.of(
                "salt", "pepper", "cumin", "paprika", "turmeric", "cinnamon", "nutmeg", "clove",
                "cardamom", "oregano", "basil", "thyme", "rosemary", "chili powder", "curry powder",
                "vanilla", "bay leaf", "saffron", "coriander seed", "mustard seed"));
        KEYWORDS.put(PantryCategory.PANTRY_STAPLE, Set.of(
                "rice", "flour", "sugar", "oil", "pasta", "noodle", "bread", "oats", "cereal",
                "vinegar", "soy sauce", "honey", "syrup", "stock", "broth", "can", "canned",
                "tomato paste", "coconut milk", "quinoa", "breadcrumb", "baking powder", "baking soda",
                "yeast"));
    }

    /** Deterministic best-guess category for a raw ingredient string (e.g. "2 large eggs"). */
    public PantryCategory categorize(String rawIngredientText) {
        if (rawIngredientText == null || rawIngredientText.isBlank()) {
            return PantryCategory.OTHER;
        }
        String lower = rawIngredientText.toLowerCase(Locale.ROOT);
        for (Map.Entry<PantryCategory, Set<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (containsAsWord(lower, keyword)) {
                    return entry.getKey();
                }
            }
        }
        return PantryCategory.OTHER;
    }

    /**
     * Word-boundary match for single-word keywords, substring for multi-word ones (a
     * word-boundary regex can't span an embedded space naturally). A bare {@code contains}
     * here previously miscategorized things like "pepperoni" as VEGETABLE (it contains
     * "pepper") - the same false-positive-substring problem fixed elsewhere in the pipeline
     * (RecipeScoringEngine, RecipeCategoryClassifier, ShoppingListService).
     */
    private boolean containsAsWord(String haystack, String keyword) {
        if (keyword.contains(" ")) {
            return haystack.contains(keyword);
        }
        return Pattern.compile("\\b" + Pattern.quote(keyword) + "\\b").matcher(haystack).find();
    }
}
