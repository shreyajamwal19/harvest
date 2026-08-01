package com.harvest.chef.repository;

import com.harvest.chef.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long>, RecipeRepositoryCustom {

    /**
     * Legacy single-phrase search, kept for compatibility. Superseded by
     * {@link RecipeRepositoryCustom#searchByIngredientTokens} for actual
     * recipe retrieval: this treats the whole term as one literal
     * substring, which breaks for anything but a single-word query (e.g.
     * "eggs cheese" would never match an ingredient line, since no
     * ingredient line literally contains that two-word phrase).
     */
    @Query("""
            SELECT DISTINCT r FROM Recipe r LEFT JOIN r.ingredients i
            WHERE LOWER(r.title) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(r.description) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(r.cuisine) LIKE LOWER(CONCAT('%', :term, '%'))
               OR LOWER(i) LIKE LOWER(CONCAT('%', :term, '%'))
            """)
    List<Recipe> searchByTerm(@Param("term") String term);
}
