package com.harvest.chef.repository;

import com.harvest.chef.entity.Recipe;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

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

        String jpql = "SELECT DISTINCT r FROM Recipe r LEFT JOIN r.ingredients i "
                + "WHERE (" + ingredientClause + ") OR (" + titleClause + ")";

        TypedQuery<Recipe> query = entityManager.createQuery(jpql, Recipe.class);
        for (int i = 0; i < cleanTokens.size(); i++) {
            query.setParameter("tok" + i, "%" + cleanTokens.get(i) + "%");
        }
        query.setMaxResults(limit);

        return query.getResultList();
    }
}
