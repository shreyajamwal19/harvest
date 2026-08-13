package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.entity.ConversationMessage;
import com.harvest.chef.entity.MessageRole;
import com.harvest.chef.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Memory Write (basic implementation).
 *
 * Persists the raw user/assistant turn pair so Context Assembly can read it back next time.
 * Durable profile-fact extraction (preferences, recipe history) happens separately via
 * PreferenceLearningService/CookingHistoryService, invoked from CompositionService/RecipeComposer
 * - this class only ever owns the raw conversation transcript.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryWriteService {

    private final ConversationMessageRepository messageRepository;

    /**
     * Never throws. A perfectly good response the user is about to receive must not be turned
     * into a 500 by a transient DB write failure here - the same "enhancement, not a dependency"
     * philosophy CookingHistoryService already applies to its own writes. The cost of a dropped
     * write is a slightly shorter memory window next turn, not a failed request right now.
     */
    @Transactional
    public void record(Long sessionId, String userMessage, ChefResponse chefResponse) {
        try {
            ConversationMessage userTurn = ConversationMessage.builder()
                    .sessionId(sessionId)
                    .role(MessageRole.USER)
                    .content(userMessage)
                    .build();
            messageRepository.save(userTurn);

            ConversationMessage assistantTurn = ConversationMessage.builder()
                    .sessionId(sessionId)
                    .role(MessageRole.ASSISTANT)
                    .content(chefResponse.getMessage())
                    .responseType(chefResponse.getType())
                    .build();
            messageRepository.save(assistantTurn);
        } catch (Exception e) {
            log.warn("Failed to persist conversation turn for session {}: {}", sessionId, e.getMessage());
        }
    }
}
