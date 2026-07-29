package com.harvest.chef.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.client.AnthropicClient;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.EvaluatedRecipe;
import com.harvest.chef.dto.GoalAssessment;
import com.harvest.chef.dto.RecipeCandidate;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Evaluates raw candidates from every recipe provider: rejects weak
 * matches, keeps only the strongest options (at most 3), and explains why
 * each survivor was chosen. Never ranks by ingredient overlap alone - that
 * was V1's fundamental flaw.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecipeEvaluationService {

    private static final int MAX_RESULTS = 3;

    private static final String SYSTEM_PROMPT = """
            You are the Recipe Evaluation stage inside Harvest's Chef Brain.
            You have been given a list of candidate recipes gathered from various sources, along
            with what the user actually wants. Act like a chef who has looked through every option
            and is only willing to hand over the ones genuinely worth cooking.

            Reject candidates that are a poor fit, redundant with a stronger candidate, or
            unrealistic given what the user described. Never keep a candidate just because its
            ingredients overlap with what the user has - fit for their actual goal matters more.

            Keep at most 3 candidates, fewer if fewer are genuinely good. It is completely fine to
            keep zero if none of the candidates are a good fit - that signals generation is needed.

            For every kept candidate, identify any ingredients it needs that the user did not
            mention having, and write one specific sentence explaining why it was selected.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "selections": [
                {
                  "candidateIndex": integer (0-based index into the candidate list provided),
                  "rationale": "one specific sentence explaining why this fits",
                  "missingIngredients": ["..."]
                }
              ]
            }
            """;

    private final AnthropicClient anthropicClient;
    private final ObjectMapper objectMapper;

    public List<EvaluatedRecipe> evaluate(ConversationContext context, GoalAssessment assessment,
                                           RetrievalPlan plan, List<RecipeCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        String raw = anthropicClient.send(SYSTEM_PROMPT, buildUserPrompt(context, assessment, plan, candidates), 700);
        return parse(raw, candidates);
    }

    private String buildUserPrompt(ConversationContext context, GoalAssessment assessment,
                                    RetrievalPlan plan, List<RecipeCandidate> candidates) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("User's latest message: ").append(context.getCurrentMessage()).append('\n');
        prompt.append("Interpreted goal: ").append(assessment.getInterpretedGoal()).append('\n');
        if (plan.getMentionedIngredients() != null && !plan.getMentionedIngredients().isEmpty()) {
            prompt.append("Ingredients the user has: ")
                    .append(String.join(", ", plan.getMentionedIngredients())).append('\n');
        }
        prompt.append("\nCandidates:\n");
        for (int i = 0; i < candidates.size(); i++) {
            RecipeCandidate candidate = candidates.get(i);
            prompt.append(i).append(") ").append(candidate.getTitle())
                    .append(" [source: ").append(candidate.getSource()).append("]\n");
            prompt.append("   Ingredients: ")
                    .append(String.join(", ", candidate.getIngredients() == null ? List.of() : candidate.getIngredients()))
                    .append('\n');
        }
        return prompt.toString();
    }

    private List<EvaluatedRecipe> parse(String raw, List<RecipeCandidate> candidates) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            JsonNode selections = node.path("selections");

            List<EvaluatedRecipe> results = new ArrayList<>();
            for (JsonNode selection : selections) {
                int index = selection.path("candidateIndex").asInt(-1);
                if (index < 0 || index >= candidates.size()) {
                    continue;
                }

                List<String> missing = new ArrayList<>();
                selection.path("missingIngredients").forEach(item -> missing.add(item.asText()));

                results.add(EvaluatedRecipe.builder()
                        .candidate(candidates.get(index))
                        .rationale(selection.path("rationale").asText(""))
                        .missingIngredients(missing)
                        .build());

                if (results.size() >= MAX_RESULTS) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse recipe evaluation JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning stage returned an unexpected evaluation format");
        }
    }
}
