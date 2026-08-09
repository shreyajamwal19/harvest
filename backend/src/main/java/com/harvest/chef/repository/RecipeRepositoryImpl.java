package com.harvest.chef.repository;

import com.harvest.chef.entity.Recipe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Backs {@link RecipeRepositoryCustom#searchByIngredientTokens}. Spring
 * Data method-name derivation and static {@code @Query} annotations can't
 * express "match any of N dynamic tokens" since N varies per call, so this
 * builds the OR/LIKE clause by hand with an EntityManager - still plain
 * JPQL (portable, safely parameterized, no string-concatenated user input:
 * every token is bound as a named parameter, never inlined into the query
 * text), just assembled at runtime instead of compile time.
 */
@Repository
public class RecipeRepositoryImpl implements RecipeRepositoryCustom {

    /** Hard ceiling on how many tokens can drive a single query, regardless of
     *  how many were parsed - keeps query complexity and latency bounded. */
    private static final int MAX_TOKENS = 8;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public List<Recipe> searchByIngredientTokens(List<String> tokens, int limit) {
        List<String> cleanTokens = tokens == null ? List.of() : tokens.stream()
                .filter(Objects::nonNull)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .filter(t -> t.length() >= 2)
                .distinct()
                .limit(MAX_TOKENS)
                .toList();

        if (cleanTokens.isEmpty()) {
            return List.of();
        }

        // Title-token-coverage pass runs first and is never starved out of the fixed-size
        // candidate pool the way a single combined OR query would starve it: with a plain OR
        // across every token and no ordering, one very common token ("chicken") alone can fill
        // the entire LIMIT with unrelated chicken recipes before the query ever reaches the row
        // where BOTH words actually appear in the title - exactly the dish the user meant by
        // "chicken alfredo". A recipe whose title itself covers more of the query's tokens is
        // unambiguously more likely to be that dish (the same reasoning RecipeScoringEngine
        // already applies via its PRIMARY ingredient-importance tier for title matches).
        List<Recipe> titleMatches = searchByTitleTokenCoverage(cleanTokens, limit);
        int remaining = limit - titleMatches.size();
        if (remaining <= 0) {
            return titleMatches;
        }

        List<Long> alreadyFound = titleMatches.stream().map(Recipe::getId).toList();
        List<Recipe> broadMatches = searchByAnyTokenAnywhere(cleanTokens, alreadyFound, remaining);

        List<Recipe> combined = new ArrayList<>(titleMatches);
        combined.addAll(broadMatches);
        return combined;
    }

    /**
     * No JOIN needed - title/description/cuisine are the recipe's own columns, not the
     * ingredient collection - so there's no row duplication and therefore no DISTINCT needed
     * either, which is what makes ordering by a computed token-coverage expression possible
     * here (Postgres rejects an ORDER BY expression that isn't in the SELECT list under
     * SELECT DISTINCT).
     */
    private List<Recipe> searchByTitleTokenCoverage(List<String> cleanTokens, int limit) {
        StringBuilder titleClause = new StringBuilder();
        StringBuilder titleMatchScore = new StringBuilder();
        for (int i = 0; i < cleanTokens.size(); i++) {
            if (i > 0) {
                titleClause.append(" OR ");
                titleMatchScore.append(" + ");
            }
            titleClause.append("LOWER(r.title) LIKE :tok").append(i)
                    .append(" OR LOWER(r.description) LIKE :tok").append(i)
                    .append(" OR LOWER(r.cuisine) LIKE :tok").append(i);
            titleMatchScore.append("(CASE WHEN LOWER(r.title) LIKE :tok").append(i).append(" THEN 1 ELSE 0 END)");
        }

        String jpql = "SELECT r FROM Recipe r WHERE (" + titleClause + ") "
                + "ORDER BY (" + titleMatchScore + ") DESC, r.id ASC";

        TypedQuery<Recipe> query = entityManager.createQuery(jpql, Recipe.class);
        bindTokens(query, cleanTokens);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    /**
     * The original broad recall pass (ingredient lines included via a LEFT JOIN, hence
     * DISTINCT to collapse the resulting row duplication), excluding whatever the title pass
     * above already surfaced so the same recipe never appears twice in one result set.
     */
    private List<Recipe> searchByAnyTokenAnywhere(List<String> cleanTokens, List<Long> excludeIds, int limit) {
        StringBuilder ingredientClause = new StringBuilder();
        StringBuilder titleClause = new StringBuilder();
        for (int i = 0; i < cleanTokens.size(); i++) {
            if (i > 0) {
                ingredientClause.append(" OR ");
                titleClause.append(" OR ");
            }
            ingredientClause.append("LOWER(i) LIKE :tok").append(i);
            titleClause.append("LOWER(r.title) LIKE :tok").append(i)
                    .append(" OR LOWER(r.description) LIKE :tok").append(i)
                    .append(" OR LOWER(r.cuisine) LIKE :tok").append(i);
        }

        String exclusion = excludeIds.isEmpty() ? "" : " AND r.id NOT IN :excludeIds";
        String jpql = "SELECT DISTINCT r FROM Recipe r LEFT JOIN r.ingredients i "
                + "WHERE ((" + ingredientClause + ") OR (" + titleClause + "))" + exclusion;

        TypedQuery<Recipe> query = entityManager.createQuery(jpql, Recipe.class);
        bindTokens(query, cleanTokens);
        if (!excludeIds.isEmpty()) {
            query.setParameter("excludeIds", excludeIds);
        }
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private void bindTokens(TypedQuery<Recipe> query, List<String> cleanTokens) {
        for (int i = 0; i < cleanTokens.size(); i++) {
            query.setParameter("tok" + i, "%" + cleanTokens.get(i) + "%");
        }
    }
}
