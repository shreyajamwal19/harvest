package com.harvest.chef.provider.memory;

import com.harvest.chef.entity.ConversationMessage;
import com.harvest.chef.entity.MessageRole;
import com.harvest.chef.knowledge.model.KnowledgeProviderType;
import com.harvest.chef.knowledge.model.ProviderHealth;
import com.harvest.chef.knowledge.model.ProviderResult;
import com.harvest.chef.knowledge.provider.UserMemoryKnowledgeProvider;
import com.harvest.chef.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Phase 2/3 implementation: surfaces the user's own recent statements from
 * other sessions as lightweight context. Not extracted/durable facts yet
 * (allergies, equipment, etc.) - that structured long-term profile is a
 * later phase, this just keeps the Chef Brain from being fully amnesiac
 * across conversations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ConversationHistoryUserMemoryProvider implements UserMemoryKnowledgeProvider {

    private static final int MAX_NOTES = 5;
    // Fetch window at the DB level, generous enough that filtering down to USER-role messages
    // afterwards still reliably finds MAX_NOTES of them, without pulling a user's entire
    // cross-session history (which grows unboundedly over the account's lifetime) on every turn.
    private static final int FETCH_WINDOW = 50;

    private final ConversationMessageRepository messageRepository;

    @Override
    public ProviderResult<List<String>> retrieve(Long userId, Long excludingSessionId) {
        long start = System.currentTimeMillis();
        try {
            List<ConversationMessage> history = messageRepository.findRecentAcrossOtherSessions(
                    userId, excludingSessionId, PageRequest.of(0, FETCH_WINDOW));

            List<String> notes = history.stream()
                    .filter(m -> m.getRole() == MessageRole.USER)
                    .limit(MAX_NOTES)
                    .map(ConversationMessage::getContent)
                    .toList();

            return ProviderResult.<List<String>>builder()
                    .data(notes)
                    .success(true)
                    .providerName(getName())
                    .confidence(notes.isEmpty() ? 0.0 : 0.7)
                    .completeness(1.0)
                    .latencyMs(System.currentTimeMillis() - start)
                    .reliability(getReliability())
                    .retrievedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.warn("User memory provider failed: {}", e.getMessage());
            return ProviderResult.failure(getName(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    @Override
    public KnowledgeProviderType getType() {
        return KnowledgeProviderType.USER_MEMORY;
    }

    @Override
    public String getName() {
        return "conversation-history-memory";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public ProviderHealth healthStatus() {
        return ProviderHealth.UP;
    }

    @Override
    public double getReliability() {
        return 0.8;
    }
}
