package com.harvest.chef.repository;

import com.harvest.chef.entity.Recipe;

import java.util.List;

/**
 * Custom (non-Spring-Data-derived) query methods for RecipeRepository.
 * Needed because matching against a variable number of ingredient/keyword
 * tokens - true "any of these N tokens" matching - isn't something a
 * static {@code @Query} or derived method name can express, since N
 * changes per request.
 */
public interface RecipeRepositoryCustom {

    /**
     * Finds recipes whose title, description, cuisine, or any ingredient
     * line matches ANY of the given tokens (case-insensitive substring
     * match), capped at {@code limit} results. This is a recall-oriented
     * broad candidate pull - ranking/precision happens afterwards in
     * RecipeEvaluationService.
     */
    List<Recipe> searchByIngredientTokens(List<String> tokens, int limit);
}
