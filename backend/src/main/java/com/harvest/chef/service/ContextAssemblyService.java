package com.harvest.chef.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.ConversationTurn;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.entity.ConversationMessage;
import com.harvest.chef.entity.ConversationSession;
import com.harvest.chef.personalization.service.UserProfileService;
import com.harvest.chef.repository.ConversationMessageRepository;
import com.harvest.chef.repository.ConversationSessionRepository;
import com.harvest.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stage 1 - Context Assembly.
 *
 * Pure bookkeeping: resolves or creates the conversation session and pulls
 * recent turns for short-term memory, plus the session's last retrieval
 * state (search query, mentioned ingredients, already-shown recipe
 * titles) so a later "more" turn can continue it. No reasoning, no LLM
 * call - this stage only assembles facts for the stages that follow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextAssemblyService {

    private final ConversationSessionRepository sessionRepository;
    private final ConversationMessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final UserProfileService userProfileService;

    @Transactional
    public ConversationContext assemble(Long userId, Long requestedSessionId, String currentMessage) {
        ConversationSession session = resolveSession(userId, requestedSessionId);
        List<ConversationTurn> recentTurns = loadRecentTurns(session.getId());

        return ConversationContext.builder()
                .sessionId(session.getId())
                .userId(userId)
                .currentMessage(currentMessage)
                .recentTurns(recentTurns)
                .lastSearchQuery(session.getLastSearchQuery())
                .lastMentionedIngredients(splitCsv(session.getLastMentionedIngredients()))
                .shownRecipeTitles(splitPipe(session.getShownRecipeTitles()))
                .lastShownRecipes(deserializeRecipes(session.getLastShownRecipesJson()))
                // Phase 6A - loaded once per turn; never throws, falls back to an empty
                // snapshot on any failure so a personalization outage never breaks a turn.
                .userProfile(userProfileService.loadSnapshot(userId))
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

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private Set<String> splitPipe(String pipeSeparated) {
        if (pipeSeparated == null || pipeSeparated.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(Arrays.stream(pipeSeparated.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
    }

    /**
     * Never lets a malformed/stale JSON blob break the whole request - worst case, follow-up
     * reasoning simply has nothing to ground against and CompositionService falls back to a
     * fresh retrieval, exactly as if no prior recipe turn existed.
     */
    private List<RecipeResponse> deserializeRecipes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<RecipeResponse>>() {
            });
        } catch (Exception e) {
            log.warn("Failed to deserialize last shown recipes for session - treating as none: {}",
                    e.getMessage());
            return List.of();
        }
    }
}
