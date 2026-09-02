package com.harvest.chef.personalization.repository;

import com.harvest.chef.personalization.entity.PreferenceCategory;
import com.harvest.chef.personalization.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    List<UserPreference> findByUserIdOrderByConfidenceDesc(Long userId);

    /** Precise, ownership-checked single-row delete for the Preferences page's per-item delete
     *  button - distinct from deleteByUserIdAndValueMatching's deliberately fuzzy fragment match,
     *  which exists for chat's "forget X" phrasing tolerance, not for deleting one exact row a
     *  person is looking straight at in the UI. */
    long deleteByIdAndUserId(Long id, Long userId);

    Optional<UserPreference> findByUserIdAndCategoryAndValue(Long userId, PreferenceCategory category, String value);

    @Modifying
    @Query("DELETE FROM UserPreference p WHERE p.userId = :userId AND LOWER(p.value) LIKE LOWER(CONCAT('%', :value, '%'))")
    int deleteByUserIdAndValueContaining(@Param("userId") Long userId, @Param("value") String value);

    // Bidirectional on purpose: "forget spicy food" should also match a stored value of just
    // "spicy" (the fragment CONTAINS the stored value), not only the reverse. Without this, a
    // forget command whose wording doesn't exactly reproduce however the original statement was
    // captured silently deletes nothing - a real gap, since the whole point of a forget command
    // is that the user doesn't have to remember Harvest's own internal phrasing.
    @Modifying
    @Query("DELETE FROM UserPreference p WHERE p.userId = :userId "
            + "AND (LOWER(p.value) LIKE LOWER(CONCAT('%', :value, '%')) "
            + "OR LOWER(:value) LIKE CONCAT('%', LOWER(p.value), '%'))")
    int deleteByUserIdAndValueMatching(@Param("userId") Long userId, @Param("value") String value);

    @Modifying
    @Query("DELETE FROM UserPreference p WHERE p.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
