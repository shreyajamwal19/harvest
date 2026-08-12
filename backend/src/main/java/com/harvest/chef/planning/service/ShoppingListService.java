package com.harvest.chef.planning.service;

import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.pantry.entity.PantryCategory;
import com.harvest.chef.pantry.service.PantryCategorizer;
import com.harvest.chef.planning.dto.ShoppingListCategory;
import com.harvest.chef.planning.dto.ShoppingListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deterministically builds a shopping list from a set of recipes: for each recipe, every
 * ingredient line NOT already covered by the pantry is kept, duplicates across recipes are
 * merged, and everything is grouped by {@link PantryCategory} for a scannable list
 * (SHOPPING_LISTS / INGREDIENT_REUSE). Recomputes pantry coverage fresh against the current
 * pantry snapshot rather than trusting any earlier-computed "missing ingredients" field, so the
 * list always reflects what's actually in the pantry right now.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShoppingListService {

    private final PantryCategorizer categorizer;

    public ShoppingListResponse generate(List<RecipeResponse> recipes, PantrySnapshot pantry) {
        List<String> pantryNames = pantry == null ? List.of() : pantry.ingredientNames();

        // LinkedHashSet per category preserves first-seen order and merges duplicates for free
        // (case-insensitive, via the normalized key already used as the set element).
        Map<PantryCategory, Set<String>> grouped = new EnumMap<>(PantryCategory.class);

        for (RecipeResponse recipe : recipes) {
            if (recipe.getIngredients() == null) {
                continue;
            }
            for (String line : recipe.getIngredients()) {
                if (line == null || line.isBlank() || isAlreadyInPantry(line, pantryNames)) {
                    continue;
                }
                PantryCategory category = categorizer.categorize(line);
                grouped.computeIfAbsent(category, c -> new LinkedHashSet<>()).add(normalize(line));
            }
        }

        List<ShoppingListCategory> categories = new ArrayList<>();
        for (Map.Entry<PantryCategory, Set<String>> entry : grouped.entrySet()) {
            categories.add(ShoppingListCategory.builder()
                    .category(entry.getKey().name())
                    .items(List.copyOf(entry.getValue()))
                    .build());
        }

        int itemCount = categories.stream().mapToInt(c -> c.getItems().size()).sum();
        log.info("[shopping-list] generated from {} recipe(s), {} item(s) across {} categories, pantry items={}",
                recipes.size(), itemCount, categories.size(), pantryNames.size());

        return ShoppingListResponse.builder().categories(categories).build();
    }

    /**
     * Word-boundary match, not bare substring - mirrors {@code containsAsWord} in
     * RecipeScoringEngine/RecipeCategoryClassifier. A plain {@code contains} here previously
     * meant a pantry item like "salt" falsely matched "unsalted butter", or "egg" falsely
     * matched "eggplant", silently hiding real shopping-list needs.
     */
    private boolean isAlreadyInPantry(String ingredientLine, List<String> pantryNames) {
        String lower = ingredientLine.toLowerCase(Locale.ROOT);
        for (String pantryName : pantryNames) {
            if (containsAsWord(lower, pantryName)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsAsWord(String haystack, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        String needleLower = needle.toLowerCase(Locale.ROOT);
        if (needleLower.contains(" ")) {
            return haystack.contains(needleLower);
        }
        return Pattern.compile("\\b" + Pattern.quote(needleLower) + "\\b").matcher(haystack).find();
    }

    private String normalize(String ingredientLine) {
        return ingredientLine.trim().toLowerCase(Locale.ROOT);
    }
}
