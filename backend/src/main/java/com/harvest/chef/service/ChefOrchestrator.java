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

        // 4. Memory Write
        memoryWriteService.record(context.getSessionId(), message, chefResponse);

        return response;
    }
}
