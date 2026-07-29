package com.harvest.chef.repository;

import com.harvest.chef.entity.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /**
     * Basic text-overlap search across title, description, cuisine, and
     * ingredient names. Not semantic retrieval - that's a later phase's
     * vector-store upgrade per the frozen architecture. Real and functional
     * as a first knowledge provider, not a placeholder.
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
