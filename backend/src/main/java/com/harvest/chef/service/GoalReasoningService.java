package com.harvest.chef.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.ConversationTurn;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.GoalSufficiency;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stage 2 - Goal Reasoning.
 *
 * The only job of this stage is to understand what the user is actually
 * trying to accomplish and judge whether there's enough to act on. It does
 * NOT propose recipes, techniques, or questions - that belongs to Composition.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GoalReasoningService {

    private static final String SYSTEM_PROMPT = """
            You are the Goal Reasoning stage inside Harvest's Chef Brain.
            Your ONLY job is to understand what the user is actually trying to accomplish \
            and decide whether there is enough information to help them cook something right now.

            You do NOT suggest recipes. You do NOT explain techniques. You ONLY assess the goal.

            Think like an experienced chef listening to someone describe their situation:
            - A single vague ingredient or craving ("I have caramel") is usually NOT enough to act on.
            - A concrete, workable set of ingredients ("eggs, spinach and rice") IS usually enough.
            - Non-cooking or ambiguous life statements ("I'm sick") need judgement: sometimes a recipe \
            request is implied, sometimes the person needs something else first - use your judgement \
            about whether cooking guidance is actually the helpful response.
            - A statement of having nothing to cook with ("I have nothing") is cooking-related but \
            cannot lead to a real recipe or a useful follow-up question - mark it NON_ACTIONABLE, \
            not INSUFFICIENT, because no follow-up question will produce ingredients that don't exist.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "cookingRelated": boolean,
              "sufficiency": "SUFFICIENT" | "INSUFFICIENT" | "NON_ACTIONABLE",
              "interpretedGoal": "one sentence describing what the user is actually trying to do",
              "missingInformation": "what's missing, or null if sufficiency is SUFFICIENT",
              "reasoningNote": "one short sentence explaining the sufficiency decision"
            }
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public GoalAssessment assess(ConversationContext context) {
        String raw = anthropicClient.send(SYSTEM_PROMPT, buildUserPrompt(context), 400);
        return parse(raw);
    }

    private String buildUserPrompt(ConversationContext context) {
        StringBuilder prompt = new StringBuilder();

        if (context.getRecentTurns() != null && !context.getRecentTurns().isEmpty()) {
            prompt.append("Conversation so far:\n");
            for (ConversationTurn turn : context.getRecentTurns()) {
                prompt.append(turn.getRole()).append(": ").append(turn.getContent()).append('\n');
            }
            prompt.append('\n');
        }

        prompt.append("Latest user message: ").append(context.getCurrentMessage());
        return prompt.toString();
    }

    private GoalAssessment parse(String raw) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);

            return GoalAssessment.builder()
                    .cookingRelated(node.path("cookingRelated").asBoolean(true))
                    .sufficiency(GoalSufficiency.valueOf(node.path("sufficiency").asText()))
                    .interpretedGoal(node.path("interpretedGoal").asText(""))
                    .missingInformation(node.path("missingInformation").isNull()
                            ? null : node.path("missingInformation").asText(null))
                    .reasoningNote(node.path("reasoningNote").asText(""))
                    .build();
        } catch (Exception e) {
            log.error("Failed to parse goal assessment JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning stage returned an unexpected format");
        }
    }
}
