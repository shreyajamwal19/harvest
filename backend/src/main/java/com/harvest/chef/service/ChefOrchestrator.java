package com.harvest.chef.service;

import com.harvest.chef.dto.ChatResponse;
import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The Chef Brain's orchestration layer. Coordinates the request pipeline:
 * Context Assembly -> Composition (which runs Retrieval Planning internally
 * as its first step) -> Response Rendering -> Memory Write.
 *
 * There is no Goal Reasoning or Sufficiency Gate stage - every request
 * flows straight from Context Assembly into Composition/Retrieval
 * Planning. Holds no reasoning or prompt logic itself - each stage is an
 * independent, injectable service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChefOrchestrator {

    private final ContextAssemblyService contextAssemblyService;
    private final CompositionService compositionService;
    private final ResponseRenderingService responseRenderingService;
    private final MemoryWriteService memoryWriteService;

    public ChatResponse handle(Long userId, Long requestedSessionId, String message) {
        // 1. Context Assembly
        ConversationContext context = contextAssemblyService.assemble(userId, requestedSessionId, message);

        // 2. Composition (runs Retrieval Planning internally, then dispatches by intent)
        ChefResponse chefResponse = compositionService.compose(context);
        log.info("Chef Brain response for session {}: type={}", context.getSessionId(), chefResponse.getType());

        // 3. Response Rendering
        ChatResponse response = responseRenderingService.render(context.getSessionId(), chefResponse);

        // 4. Memory Write - guarded here, not just inside MemoryWriteService itself: once
        // Hibernate flags a @Transactional method's transaction rollback-only from a flush-time
        // SQL error, Spring throws UnexpectedRollbackException at commit, which happens *after*
        // the method body (and its own try/catch) has already returned - so a persistence
        // failure in there can otherwise destroy an already-computed, perfectly good response.
        try {
            memoryWriteService.record(context.getSessionId(), message, chefResponse);
        } catch (Exception e) {
            log.warn("Memory write failed for session {} - the response below is still returned; "
                    + "only this turn's history may be missing next time: {}",
                    context.getSessionId(), e.getMessage());
        }

        return response;
    }
}
