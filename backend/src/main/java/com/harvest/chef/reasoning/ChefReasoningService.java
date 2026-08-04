package com.harvest.chef.reasoning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.llm.LLMProviderManager;
import com.harvest.chef.llm.LLMResult;
import com.harvest.chef.reasoning.prompt.ChefCoachingPromptBuilder;
import com.harvest.chef.reasoning.prompt.LLMPrompt;
import com.harvest.chef.reasoning.prompt.RecipeAdaptationPromptBuilder;
import com.harvest.chef.reasoning.prompt.RecipeComparisonPromptBuilder;
import com.harvest.chef.reasoning.prompt.RecipeExplanationPromptBuilder;
import com.harvest.chef.reasoning.prompt.TechniqueExplanationPromptBuilder;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The AI Chef Reasoning Layer sits between the deterministic pipeline (retrieval, scoring,
 * ranking - all unchanged and still the source of truth) and Response Rendering. It is called
 * from three places, one per broad situation, each dispatching to the {@link ReasoningMode}
 * that fits and the matching prompt builder in {@code com.harvest.chef.reasoning.prompt}:
 *
 * <ul>
 *   <li>{@code RecipeComposer} - {@link #reasonAboutRecipes}, right after the deterministic
 *       engine has ranked candidates for the current turn.</li>
 *   <li>{@code CompositionService} - {@link #reasonAboutFollowUp}, for a detected follow-up
 *       turn (comparison, adaptation, coaching, or "explain why"), grounded only in the
 *       recipe(s) already shown earlier this session.</li>
 *   <li>{@code TechniqueAnswerComposer} - {@link #reasonAboutTechnique}, for a standalone
 *       cooking-technique question, with or without a grounded answer from
 *       {@code KnowledgeProviderManager}.</li>
 * </ul>
 *
 * This service NEVER searches, ranks, or retrieves recipes, and never returns a recipe the
 * deterministic pipeline didn't already provide - it only ever produces a conversational
 * {@code message} (see {@link ChefReasoningResult}), plus the {@code mode}/{@code confidence}
 * it reasoned under, logged here for observability. It is provider-agnostic: which actual LLM
 * (Gemini, Groq, or OpenAI) served a given call is entirely {@link LLMProviderManager}'s
 * concern. If every provider is unavailable or every call fails, every method here returns
 * {@link Optional#empty()} so callers fall back to the deterministic pipeline's own output; the
 * app keeps functioning identically to before this layer existed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChefReasoningService {

    private static final int RECIPE_REASONING_MAX_TOKENS = 400;
    private static final int FOLLOW_UP_MAX_TOKENS = 350;
    private static final int TECHNIQUE_MAX_TOKENS = 350;

    private final LLMProviderManager llmProviderManager;
    private final ObjectMapper objectMapper;

    private final RecipeExplanationPromptBuilder recipeExplanationPromptBuilder;
    private final RecipeComparisonPromptBuilder recipeComparisonPromptBuilder;
    private final RecipeAdaptationPromptBuilder recipeAdaptationPromptBuilder;
    private final ChefCoachingPromptBuilder chefCoachingPromptBuilder;
    private final TechniqueExplanationPromptBuilder techniqueExplanationPromptBuilder;

    /**
     * Called by {@code RecipeComposer} after the deterministic engine has produced its final
     * ranked recipe list for this turn. {@code rankedRecipes} is exactly what will be shown to
     * the user regardless of what this method returns - only the conversational message (and,
     * when the list is empty, the response type) can come from here.
     */
    public Optional<ChefReasoningResult> reasonAboutRecipes(ConversationContext context, RetrievalPlan plan,
                                                              List<RecipeResponse> rankedRecipes) {
        LLMPrompt prompt = recipeExplanationPromptBuilder.buildForInitialTurn(context, plan, rankedRecipes);
        return callAndParseExplanation(prompt, rankedRecipes.isEmpty(), ReasoningMode.RECIPE_EXPLANATION,
                ReasoningConfidence.HIGH);
    }

    /**
     * Called by {@code CompositionService} when {@code FollowUpIntentDetector} classifies the
     * current message as a follow-up about recipe(s) already shown this session. Retrieval is
     * skipped entirely for these turns - the only grounding is {@code previouslyShownRecipes},
     * loaded from session state. {@code mode} selects both the prompt builder and the
     * deterministic confidence label logged with the result.
     */
    public Optional<ChefReasoningResult> reasonAboutFollowUp(ConversationContext context, ReasoningMode mode,
                                                               List<RecipeResponse> previouslyShownRecipes) {
        if (previouslyShownRecipes == null || previouslyShownRecipes.isEmpty()) {
            return Optional.empty();
        }

        LLMPrompt prompt = switch (mode) {
            case RECIPE_EXPLANATION ->
                    recipeExplanationPromptBuilder.buildForExplainWhyFollowUp(context, previouslyShownRecipes);
            case RECIPE_COMPARISON -> recipeComparisonPromptBuilder.build(context, previouslyShownRecipes);
            case RECIPE_ADAPTATION -> recipeAdaptationPromptBuilder.build(context, previouslyShownRecipes);
            case CHEF_COACHING -> chefCoachingPromptBuilder.build(context, previouslyShownRecipes);
            case TECHNIQUE_EXPLANATION -> throw new IllegalArgumentException(
                    "TECHNIQUE_EXPLANATION is handled via reasonAboutTechnique, not reasonAboutFollowUp");
        };

        ReasoningConfidence confidence = switch (mode) {
            case RECIPE_EXPLANATION, RECIPE_COMPARISON -> ReasoningConfidence.HIGH;
            case RECIPE_ADAPTATION -> ReasoningConfidence.MEDIUM;
            case CHEF_COACHING, TECHNIQUE_EXPLANATION -> ReasoningConfidence.LOW;
        };

        if (mode == ReasoningMode.RECIPE_EXPLANATION) {
            return callAndParseExplanation(prompt, previouslyShownRecipes.isEmpty(), mode, confidence);
        }
        return callAndParseMessageOnly(prompt, mode, confidence, FOLLOW_UP_MAX_TOKENS);
    }

    /**
     * Called by {@code TechniqueAnswerComposer} for a standalone technique/knowledge question.
     * {@code groundedAnswer} is whatever the deterministic {@code KnowledgeProviderManager}
     * found (null if nothing is grounded, which is currently always true - no provider is
     * registered) - see {@link TechniqueExplanationPromptBuilder} for how that changes the
     * prompt and confidence.
     */
    public Optional<ChefReasoningResult> reasonAboutTechnique(ConversationContext context, String groundedAnswer) {
        LLMPrompt prompt = techniqueExplanationPromptBuilder.build(context, groundedAnswer);
        ReasoningConfidence confidence = (groundedAnswer != null && !groundedAnswer.isBlank())
                ? ReasoningConfidence.HIGH
                : ReasoningConfidence.LOW;
        return callAndParseMessageOnly(prompt, ReasoningMode.TECHNIQUE_EXPLANATION, confidence, TECHNIQUE_MAX_TOKENS);
    }

    // ---------------------------------------------------------------- shared call/parse helpers

    private Optional<ChefReasoningResult> callAndParseExplanation(LLMPrompt prompt, boolean noRecipesRetrieved,
                                                                    ReasoningMode mode,
                                                                    ReasoningConfidence confidence) {
        Optional<LLMResult> result =
                llmProviderManager.complete(prompt.systemPrompt(), prompt.userPrompt(), RECIPE_REASONING_MAX_TOKENS);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        try {
            String cleaned = JsonExtractionUtil.stripCodeFences(result.get().text());
            JsonNode node = objectMapper.readTree(cleaned);
            String message = node.path("message").asText("");
            if (message.isBlank()) {
                throw new ChefReasoningException("The AI reasoning layer returned an empty message");
            }

            // Safety net independent of the model's own compliance: a CLARIFYING_QUESTION
            // response is only ever honored when the deterministic engine truly found nothing,
            // so the reasoning layer can never suppress real, ranked results.
            String requestedType = node.path("responseType").asText("RECIPE");
            ChefResponseType type = ("CLARIFYING_QUESTION".equals(requestedType) && noRecipesRetrieved)
                    ? ChefResponseType.CLARIFYING_QUESTION
                    : ChefResponseType.RECIPE;

            logSuccess(mode, confidence, result.get());
            return Optional.of(ChefReasoningResult.builder()
                    .type(type).message(message).mode(mode).confidence(confidence).build());
        } catch (Exception e) {
            log.warn("[ai-chef] mode={} provider={} returned an unusable response, falling back to "
                    + "deterministic output: {}", mode, result.get().providerName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<ChefReasoningResult> callAndParseMessageOnly(LLMPrompt prompt, ReasoningMode mode,
                                                                    ReasoningConfidence confidence, int maxTokens) {
        Optional<LLMResult> result = llmProviderManager.complete(prompt.systemPrompt(), prompt.userPrompt(), maxTokens);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        try {
            String cleaned = JsonExtractionUtil.stripCodeFences(result.get().text());
            JsonNode node = objectMapper.readTree(cleaned);
            String message = node.path("message").asText("");
            if (message.isBlank()) {
                throw new ChefReasoningException("The AI reasoning layer returned an empty message");
            }

            logSuccess(mode, confidence, result.get());
            return Optional.of(ChefReasoningResult.builder()
                    .type(ChefResponseType.RECIPE).message(message).mode(mode).confidence(confidence).build());
        } catch (Exception e) {
            log.warn("[ai-chef] mode={} provider={} returned an unusable response, falling back to "
                    + "deterministic output: {}", mode, result.get().providerName(), e.getMessage());
            return Optional.empty();
        }
    }

    private void logSuccess(ReasoningMode mode, ReasoningConfidence confidence, LLMResult result) {
        log.info("[ai-chef] mode={} confidence={} provider={} latencyMs={} inputTokens={} outputTokens={}",
                mode, confidence, result.providerName(), result.latencyMs(),
                result.inputTokens(), result.outputTokens());
    }
}
