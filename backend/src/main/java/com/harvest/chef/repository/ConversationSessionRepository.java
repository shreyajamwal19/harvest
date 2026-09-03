package com.harvest.chef.repository;

import com.harvest.chef.entity.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationSessionRepository extends JpaRepository<ConversationSession, Long> {
    Optional<ConversationSession> findByIdAndUserId(Long id, Long userId);

    @Modifying
    @Query("DELETE FROM ConversationSession s WHERE s.userId = :userId")
    int deleteAllByUserId(@Param("userId") Long userId);
}
