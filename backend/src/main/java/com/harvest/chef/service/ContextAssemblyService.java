package com.harvest.chef.service;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.ConversationTurn;
import com.harvest.chef.entity.ConversationMessage;
import com.harvest.chef.entity.ConversationSession;
import com.harvest.chef.repository.ConversationMessageRepository;
import com.harvest.chef.repository.ConversationSessionRepository;
import com.harvest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Stage 1 - Context Assembly.
 *
 * Pure bookkeeping: resolves or creates the conversation session and pulls
 * recent turns for short-term memory. No reasoning, no LLM call - this stage
 * only assembles facts for the stages that follow.
 */
@Service
@RequiredArgsConstructor
public class ContextAssemblyService {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;

    @Transactional
    public ConversationContext assemble(Long userId, Long requestedSessionId, String currentMessage) {
        ConversationSession session = resolveSession(userId, requestedSessionId);
        List<ConversationTurn> recentTurns = loadRecentTurns(session.getId());

        return ConversationContext.builder()
                .sessionId(session.getId())
                .userId(userId)
                .currentMessage(currentMessage)
                .recentTurns(recentTurns)
                .build();
    }

    private ConversationSession resolveSession(Long userId, Long requestedSessionId) {
        if (requestedSessionId != null) {
            return sessionRepository.findByIdAndUserId(requestedSessionId, userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation session not found"));
        }

        ConversationSession newSession = ConversationSession.builder()
                .userId(userId)
                .build();
        return sessionRepository.save(newSession);
    }

    private List<ConversationTurn> loadRecentTurns(Long sessionId) {
        List<ConversationMessage> history =
                messageRepository.findTop10BySessionIdOrderByCreatedAtDesc(sessionId);

        List<ConversationTurn> turns = new ArrayList<>(history.size());
        for (ConversationMessage message : history) {
            turns.add(new ConversationTurn(message.getRole().name().toLowerCase(), message.getContent()));
        }
        Collections.reverse(turns); // chronological order for the reasoning stages
        return turns;
    }
}
