package com.harvest.chef.pantry.repository;

import com.harvest.chef.pantry.entity.PantryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PantryItemRepository extends JpaRepository<PantryItem, Long> {

    List<PantryItem> findByUserIdOrderByIngredientNameAsc(Long userId);

    Optional<PantryItem> findByUserIdAndIngredientName(Long userId, String ingredientName);

    /** Ownership-scoped lookup so the REST API can never let a user delete another user's item. */
    Optional<PantryItem> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM PantryItem p WHERE p.userId = :userId AND LOWER(p.ingredientName) LIKE LOWER(CONCAT('%', :fragment, '%'))")
    int deleteByUserIdAndIngredientNameContaining(@Param("userId") Long userId, @Param("fragment") String fragment);

    @Modifying
    @Query("DELETE FROM PantryItem p WHERE p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
