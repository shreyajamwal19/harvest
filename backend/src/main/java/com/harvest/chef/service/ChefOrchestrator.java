package com.harvest.chef.service;

import com.harvest.chef.dto.ChatResponse;
import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.GoalSufficiency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * The Chef Brain's orchestration layer. Coordinates the cognitive loop:
 * Context Assembly -> Goal Reasoning -> Sufficiency Gate -> Composition ->
 * Response Rendering -> Memory Write.
 *
 * Holds no reasoning or prompt logic itself - each stage is an independent,
 * injectable service, so future phases (retrieval tools, richer memory,
 * more composers) plug in here without touching this coordination logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChefOrchestrator {

    private final ContextAssemblyService contextAssemblyService;
    private final GoalReasoningService goalReasoningService;
    private final SufficiencyGateService sufficiencyGateService;
    private final CompositionService compositionService;
    private final ResponseRenderingService responseRenderingService;
    private final MemoryWriteService memoryWriteService;

    public ChatResponse handle(Long userId, Long requestedSessionId, String message) {
        // 1. Context Assembly
        ConversationContext context = contextAssemblyService.assemble(userId, requestedSessionId, message);

        // 2. Goal Reasoning
        GoalAssessment assessment = goalReasoningService.assess(context);

        // 3. Sufficiency Gate
        GoalSufficiency decision = sufficiencyGateService.decide(assessment);
        log.info("Chef Brain decision for session {}: {} ({})",
                context.getSessionId(), decision, assessment.getReasoningNote());

        // 5. Composition
        ChefResponse chefResponse = compositionService.compose(context, assessment, decision);

        // 7. Response Rendering
        ChatResponse response = responseRenderingService.render(context.getSessionId(), chefResponse);

        // 8. Memory Write
        memoryWriteService.record(context.getSessionId(), message, chefResponse);

        return response;
    }
}
