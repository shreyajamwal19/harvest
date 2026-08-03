package com.harvest.chef.reasoning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.ConversationTurn;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.llm.LLMProviderManager;
import com.harvest.chef.llm.LLMResult;
import com.harvest.chef.util.JsonExtractionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * The AI Chef Reasoning Layer sits between the deterministic pipeline
 * (retrieval, scoring, ranking - all unchanged and still the source of
 * truth) and Response Rendering. It is called in exactly two places:
 *
 * <ul>
 *   <li>{@code RecipeComposer}, after the deterministic engine has already
 *       retrieved and ranked real candidates, to interpret the request,
 *       compare the ranked options, and explain/recommend conversationally.</li>
 *   <li>{@code CompositionService}, for a detected follow-up turn ("make it
 *       vegetarian", "double it", "use an air fryer"), grounded only in the
 *       specific recipe(s) already shown earlier this session.</li>
 * </ul>
 *
 * This service NEVER searches, ranks, or retrieves recipes, and never
 * returns a recipe the deterministic pipeline didn't already provide - it
 * only ever produces a conversational {@code message} (see
 * {@link ChefReasoningResult}). It is provider-agnostic: which actual LLM
 * (Gemini, Groq, or OpenAI) served a given call is entirely
 * {@link LLMProviderManager}'s concern. If every provider is unavailable or
 * every call fails, every method here returns {@link Optional#empty()} so
 * callers fall back to the deterministic pipeline's own output; the app
 * keeps functioning identically to before this layer existed.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChefReasoningService {

    private static final int MAX_RECENT_TURNS = 4;
    private static final int RECIPE_REASONING_MAX_TOKENS = 400;
    private static final int FOLLOW_UP_MAX_TOKENS = 350;

    private static final String RECIPE_REASONING_SYSTEM_PROMPT = """
            You are the AI Chef reasoning layer inside Harvest's Chef Brain. A deterministic
            retrieval and scoring engine has already searched Harvest's recipe catalog and ranked
            the real candidates below. Your ONLY job is to reason over this already-retrieved data
            and talk to the user like a knowledgeable chef discussing real options - you are not a
            search engine and not a recipe generator.

            STRICT RULES:
            - NEVER invent a recipe, ingredient, step, or nutrition fact that is not in the data below.
            - NEVER contradict the retrieved metadata (title, ingredients, steps, servings, source).
            - You may compare the given recipes, explain trade-offs, and recommend the best fit for
              what the user actually asked for (time constraints, who they're cooking for, what they
              want to avoid, etc).
            - If zero recipes are listed below, say so honestly and, if it would genuinely help, ask
              ONE short clarifying question - never fabricate a recipe to fill the gap.
            - If recipes ARE listed but the request is vague (e.g. just one bare ingredient with no
              clear meal type), you may briefly ask what kind of meal they're after as part of your
              message while still pointing to the options below - do not refuse to answer.
            - Do not mention scores, algorithms, ranking, or the pipeline itself - talk like a chef.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "responseType": "RECIPE" | "CLARIFYING_QUESTION",
              "message": "your conversational response, 1-4 sentences"
            }
            "responseType" may only be "CLARIFYING_QUESTION" when zero recipes are listed below.
            """;

    private static final String FOLLOW_UP_SYSTEM_PROMPT = """
            You are the AI Chef reasoning layer inside Harvest's Chef Brain, handling a follow-up
            question about a recipe you already showed the user (listed below).

            STRICT RULES:
            - NEVER invent a new recipe or claim a different retrieved recipe exists.
            - Work only from the recipe(s) already shown below. You may use ordinary cooking
              knowledge to explain how to adapt them (e.g. a vegetarian swap, doubling quantities,
              an air fryer conversion, cutting a step to save time) - but be clear you are adapting
              the shown recipe, not retrieving a new one.
            - If the request can't be reasoned about from the recipe(s) below (e.g. it refers to a
              dish never shown), say so honestly instead of guessing.
            - Do not mention scores, algorithms, or the pipeline itself - talk like a chef.

            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            { "message": "your conversational response" }
            """;

    private final LLMProviderManager llmProviderManager;
    private final ObjectMapper objectMapper;

    /**
     * Called by {@code RecipeComposer} after the deterministic engine has produced its final
     * ranked recipe list for this turn. {@code rankedRecipes} is exactly what will be shown to
     * the user regardless of what this method returns - only the conversational message (and,
     * when the list is empty, the response type) can come from here.
     */
    public Optional<ChefReasoningResult> reasonAboutRecipes(ConversationContext context, RetrievalPlan plan,
                                                              List<RecipeResponse> rankedRecipes) {
        String userPrompt = buildRecipeReasoningPrompt(context, plan, rankedRecipes);
        Optional<LLMResult> result =
                llmProviderManager.complete(RECIPE_REASONING_SYSTEM_PROMPT, userPrompt, RECIPE_REASONING_MAX_TOKENS);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(parseRecipeReasoning(result.get().text(), rankedRecipes.isEmpty()));
        } catch (ChefReasoningException e) {
            log.warn("[ai-chef] provider={} returned an unusable recipe-reasoning response, falling back to "
                    + "deterministic summary: {}", result.get().providerName(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Called by {@code CompositionService} when {@code FollowUpIntentDetector} recognizes the
     * current message as a follow-up about recipe(s) already shown this session. Retrieval is
     * skipped entirely for these turns - the only grounding is {@code previouslyShownRecipes},
     * loaded from session state.
     */
    public Optional<ChefReasoningResult> reasonAboutFollowUp(ConversationContext context,
                                                               List<RecipeResponse> previouslyShownRecipes) {
        if (previouslyShownRecipes == null || previouslyShownRecipes.isEmpty()) {
            return Optional.empty();
        }

        String userPrompt = buildFollowUpPrompt(context, previouslyShownRecipes);
        Optional<LLMResult> result =
                llmProviderManager.complete(FOLLOW_UP_SYSTEM_PROMPT, userPrompt, FOLLOW_UP_MAX_TOKENS);
        if (result.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(parseFollowUp(result.get().text()));
        } catch (ChefReasoningException e) {
            log.warn("[ai-chef] provider={} returned an unusable follow-up response: {}",
                    result.get().providerName(), e.getMessage());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- prompt building

    private String buildRecipeReasoningPrompt(ConversationContext context, RetrievalPlan plan,
                                               List<RecipeResponse> rankedRecipes) {
        StringBuilder prompt = new StringBuilder();
        appendRecentTurns(prompt, context);
        prompt.append("User's latest message: ").append(context.getCurrentMessage()).append('\n');

        if (plan.getMentionedIngredients() != null && !plan.getMentionedIngredients().isEmpty()) {
            prompt.append("Ingredients the user has: ")
                    .append(String.join(", ", plan.getMentionedIngredients())).append('\n');
        }
        if (plan.getPreferenceTags() != null && !plan.getPreferenceTags().isEmpty()) {
            prompt.append("Detected preferences: ")
                    .append(String.join(", ", plan.getPreferenceTags())).append('\n');
        }
        if (plan.isContinuation()) {
            prompt.append("This is a \"more\" turn - the user wants additional options beyond what "
                    + "was already shown.\n");
        }

        prompt.append("\nRetrieved recipes (already ranked by the deterministic engine, most relevant first):\n");
        if (rankedRecipes.isEmpty()) {
            prompt.append("(none - the deterministic engine found nothing suitable)\n");
        } else {
            appendRecipeBlocks(prompt, rankedRecipes);
        }
        return prompt.toString();
    }

    private String buildFollowUpPrompt(ConversationContext context, List<RecipeResponse> previouslyShownRecipes) {
        StringBuilder prompt = new StringBuilder();
        appendRecentTurns(prompt, context);
        prompt.append("User's follow-up message: ").append(context.getCurrentMessage()).append('\n');
        prompt.append("\nRecipe(s) already shown this session:\n");
        appendRecipeBlocks(prompt, previouslyShownRecipes);
        return prompt.toString();
    }

    private void appendRecentTurns(StringBuilder prompt, ConversationContext context) {
        List<ConversationTurn> turns = context.getRecentTurns();
        if (turns == null || turns.isEmpty()) {
            return;
        }
        int fromIndex = Math.max(0, turns.size() - MAX_RECENT_TURNS);
        prompt.append("Recent conversation:\n");
        for (ConversationTurn turn : turns.subList(fromIndex, turns.size())) {
            prompt.append(turn.getRole()).append(": ").append(turn.getContent()).append('\n');
        }
        prompt.append('\n');
    }

    private void appendRecipeBlocks(StringBuilder prompt, List<RecipeResponse> recipes) {
        int index = 1;
        for (RecipeResponse recipe : recipes) {
            prompt.append(index++).append(") ").append(recipe.getTitle())
                    .append(" [source: ").append(recipe.getSource()).append(']').append('\n');
            if (recipe.getServings() != null) {
                prompt.append("   Servings: ").append(recipe.getServings()).append('\n');
            }
            prompt.append("   Ingredients: ")
                    .append(String.join(", ", nullSafe(recipe.getIngredients()))).append('\n');
            if (recipe.getMissingIngredients() != null && !recipe.getMissingIngredients().isEmpty()) {
                prompt.append("   Missing from what the user has: ")
                        .append(String.join(", ", recipe.getMissingIngredients())).append('\n');
            }
            if (recipe.getRationale() != null && !recipe.getRationale().isBlank()) {
                prompt.append("   Why it was retrieved: ").append(recipe.getRationale()).append('\n');
            }
            if (recipe.getSteps() != null && !recipe.getSteps().isEmpty()) {
                prompt.append("   Steps: ").append(String.join(" | ", recipe.getSteps())).append('\n');
            }
        }
    }

    private List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }

    // ---------------------------------------------------------------- response parsing

    private ChefReasoningResult parseRecipeReasoning(String raw, boolean noRecipesRetrieved) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String message = node.path("message").asText("");
            if (message.isBlank()) {
                throw new ChefReasoningException("The AI reasoning layer returned an empty message");
            }

            // Safety net independent of the model's own compliance: a CLARIFYING_QUESTION
            // response is only ever honored when the deterministic engine truly found
            // nothing, so the reasoning layer can never suppress real, ranked results.
            String requestedType = node.path("responseType").asText("RECIPE");
            ChefResponseType type = ("CLARIFYING_QUESTION".equals(requestedType) && noRecipesRetrieved)
                    ? ChefResponseType.CLARIFYING_QUESTION
                    : ChefResponseType.RECIPE;

            return ChefReasoningResult.builder().type(type).message(message).build();
        } catch (ChefReasoningException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ai-chef] Failed to parse recipe reasoning JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning layer returned an unexpected format", e);
        }
    }

    private ChefReasoningResult parseFollowUp(String raw) {
        String cleaned = JsonExtractionUtil.stripCodeFences(raw);
        try {
            JsonNode node = objectMapper.readTree(cleaned);
            String message = node.path("message").asText("");
            if (message.isBlank()) {
                throw new ChefReasoningException("The AI reasoning layer returned an empty message");
            }
            return ChefReasoningResult.builder().type(ChefResponseType.RECIPE).message(message).build();
        } catch (ChefReasoningException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[ai-chef] Failed to parse follow-up reasoning JSON: {}", raw, e);
            throw new ChefReasoningException("The AI reasoning layer returned an unexpected format", e);
        }
    }
}
