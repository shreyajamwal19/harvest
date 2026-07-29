package com.harvest.chef.provider.memory;

import com.harvest.chef.entity.ConversationMessage;
import com.harvest.chef.entity.MessageRole;
import com.harvest.chef.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 2 implementation: surfaces the user's own recent statements from
 * other sessions as lightweight context. Not extracted/durable facts yet
 * (allergies, equipment, etc.) - that structured long-term profile is a
 * later phase, this just keeps the Chef Brain from being fully amnesiac
 * across conversations.
 */
@Component
@RequiredArgsConstructor
public class ConversationHistoryUserMemoryProvider implements UserMemoryProvider {

    private static final int MAX_NOTES = 5;

    private final ConversationMessageRepository messageRepository;

    @Override
    public List<String> recentContextFor(Long userId, Long excludingSessionId) {
        List<ConversationMessage> history =
                messageRepository.findRecentAcrossOtherSessions(userId, excludingSessionId);

        return history.stream()
                .filter(m -> m.getRole() == MessageRole.USER)
                .limit(MAX_NOTES)
                .map(ConversationMessage::getContent)
                .toList();
    }
}
