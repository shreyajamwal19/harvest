package com.harvest.chef.service.composer;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.knowledge.manager.KnowledgeProviderManager;
import com.harvest.chef.knowledge.model.IngredientProfile;
import com.harvest.chef.reasoning.ChefReasoningResult;
import com.harvest.chef.reasoning.ChefReasoningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Used when the Retrieval Orchestrator classifies the request as TECHNIQUE, not RECIPE. When
 * the plan flags ingredient intelligence as relevant (e.g. "what can I substitute for
 * buttermilk"), that context is pulled in and folded into the question passed downstream.
 *
 * No cooking-knowledge or ingredient-intelligence provider is currently registered (their only
 * implementations were LLM-backed and have been removed) - {@link KnowledgeProviderManager}
 * will honestly return empty/null rather than any provider fabricating an answer.
 *
 * The AI Chef Reasoning Layer ({@link ChefReasoningService#reasonAboutTechnique}) then explains
 * the answer in chef voice: when {@code KnowledgeProviderManager} did have a grounded answer,
 * the model must not contradict it; when it didn't (currently always), the model still answers
 * using general culinary knowledge - this is exactly the "explain techniques" / "chef coaching"
 * capability the reasoning layer exists for, just marked lower confidence (see
 * {@code ReasoningConfidence}). If the reasoning layer is unavailable or fails, this falls back
 * to the raw grounded answer (or, if there wasn't one either, the same honest "nothing grounded
 * yet" message this composer has always returned) - never a 503, and never silently different
 * behavior when zero API keys are configured versus before this layer existed.
 */
@Component
@RequiredArgsConstructor
public class TechniqueAnswerComposer implements ResponseComposer {

    private final KnowledgeProviderManager knowledgeProviderManager;
    private final ChefReasoningService chefReasoningService;

    @Override
    public ChefResponse compose(ConversationContext context, RetrievalPlan plan) {
        String question = context.getCurrentMessage();

        if (plan != null && plan.isNeedsIngredientIntelligence()
                && plan.getMentionedIngredients() != null && !plan.getMentionedIngredients().isEmpty()) {
            List<IngredientProfile> profiles =
                    knowledgeProviderManager.retrieveIngredientIntelligence(plan.getMentionedIngredients());
            question = appendIngredientContext(question, profiles);
        }

        String groundedAnswer = knowledgeProviderManager.retrieveCookingKnowledge(question, context.getCurrentMessage());

        Optional<ChefReasoningResult> reasoning = chefReasoningService.reasonAboutTechnique(context, groundedAnswer);
        String message = reasoning.map(ChefReasoningResult::getMessage)
                .orElseGet(() -> fallbackMessage(groundedAnswer));

        return ChefResponse.builder()
                .type(ChefResponseType.TECHNIQUE_ANSWER)
                .message(message)
                .recipes(null)
                .build();
    }

    private String fallbackMessage(String groundedAnswer) {
        if (groundedAnswer != null) {
            return groundedAnswer;
        }
        return "I don't have a grounded technique answer for that right now - "
                + "no cooking-knowledge source has that covered yet.";
    }

    private String appendIngredientContext(String question, List<IngredientProfile> profiles) {
        if (profiles.isEmpty()) {
            return question;
        }
        StringBuilder enriched = new StringBuilder(question).append("\n\nKnown ingredient facts:\n");
        for (IngredientProfile profile : profiles) {
            enriched.append("- ").append(profile.getName()).append(": substitutes [")
                    .append(String.join(", ", profile.getSubstitutes() == null ? List.of() : profile.getSubstitutes()))
                    .append("], pairs with [")
                    .append(String.join(", ", profile.getFlavorPairings() == null ? List.of() : profile.getFlavorPairings()))
                    .append(']').append('\n');
        }
        return enriched.toString();
    }
}
