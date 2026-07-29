package com.harvest.chef.provider.recipe;

import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.entity.Recipe;
import com.harvest.chef.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Wraps the local Recipe table. One provider among several - the Chef
 * Brain's intelligence no longer depends on this table being large or
 * complete; it's just the fastest, most reliable source to check first.
 */
@Component
@RequiredArgsConstructor
public class LocalRecipeProvider implements RecipeProvider {

    private final RecipeRepository recipeRepository;

    @Override
    public List<RecipeCandidate> search(String query) {
        List<Recipe> matches = recipeRepository.searchByTerm(query);
        return matches.stream()
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public String providerName() {
        return "local";
    }

    private RecipeCandidate toCandidate(Recipe recipe) {
        return RecipeCandidate.builder()
                .title(recipe.getTitle())
                .description(recipe.getDescription())
                .servings(recipe.getServings())
                .ingredients(recipe.getIngredients())
                .steps(recipe.getSteps())
                .source(providerName())
                .build();
    }
}
