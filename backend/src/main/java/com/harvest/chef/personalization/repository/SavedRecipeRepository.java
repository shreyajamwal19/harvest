package com.harvest.chef.personalization.repository;

import com.harvest.chef.personalization.entity.SavedRecipe;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedRecipeRepository extends JpaRepository<SavedRecipe, Long> {

    List<SavedRecipe> findByUserIdOrderBySavedAtDesc(Long userId);

    Optional<SavedRecipe> findByUserIdAndRecipeTitle(Long userId, String recipeTitle);

    /** Ownership-scoped lookup so the REST API can never let a user delete another user's save. */
    Optional<SavedRecipe> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndRecipeTitle(Long userId, String recipeTitle);
}
