package com.harvest.chef.repository;

import com.harvest.chef.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /** Most recent turns first; callers reverse to chronological order. Basic session memory only. */
    List<ConversationMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);

    /**
     * Recent messages from the user's *other* sessions - the basis for the
     * User Memory provider's lightweight cross-conversation context.
     */
    @Query("""
            SELECT m FROM ConversationMessage m
            WHERE m.sessionId IN (
                SELECT s.id FROM ConversationSession s
                WHERE s.userId = :userId AND s.id <> :excludingSessionId
            )
            ORDER BY m.createdAt DESC
            """)
    List<ConversationMessage> findRecentAcrossOtherSessions(
            @Param("userId") Long userId,
            @Param("excludingSessionId") Long excludingSessionId);
}
