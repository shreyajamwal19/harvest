package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.entity.ConversationMessage;
import com.harvest.chef.entity.MessageRole;
import com.harvest.chef.repository.ConversationMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 8 - Memory Write (basic implementation).
 *
 * Persists the raw user/assistant turn pair so Context Assembly can read it
 * back next time. Deliberately does not extract durable profile facts yet -
 * that's the long-term memory work of a later phase.
 */
@Service
@RequiredArgsConstructor
public class MemoryWriteService {

    private final ConversationMessageRepository messageRepository;

    @Transactional
    public void record(Long sessionId, String userMessage, ChefResponse chefResponse) {
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
    }
}
