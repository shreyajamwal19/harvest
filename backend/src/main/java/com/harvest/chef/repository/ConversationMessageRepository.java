package com.harvest.chef.repository;

import com.harvest.chef.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    /** Most recent turns first; callers reverse to chronological order. Basic session memory only. */
    List<ConversationMessage> findTop10BySessionIdOrderByCreatedAtDesc(Long sessionId);
}
