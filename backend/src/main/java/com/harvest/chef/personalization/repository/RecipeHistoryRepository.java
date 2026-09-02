package com.harvest.chef.personalization.repository;

import com.harvest.chef.personalization.entity.RecipeHistoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeHistoryRepository extends JpaRepository<RecipeHistoryEntry, Long> {

    List<RecipeHistoryEntry> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);

    /** COOKED only, most-recent-first - the Cooking History page's data source. Everything else
     *  in the table (VIEWED/REPEATED especially) is logged on every single chat turn and would
     *  drown out the handful of times someone actually finished cooking something. */
    List<RecipeHistoryEntry> findTop50ByUserIdAndEventTypeOrderByCreatedAtDesc(
            Long userId, com.harvest.chef.personalization.entity.HistoryEventType eventType);

    long countByUserIdAndRecipeTitleAndEventType(Long userId, String recipeTitle,
                                                  com.harvest.chef.personalization.entity.HistoryEventType eventType);

    @Modifying
    @Query("DELETE FROM RecipeHistoryEntry h WHERE h.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
