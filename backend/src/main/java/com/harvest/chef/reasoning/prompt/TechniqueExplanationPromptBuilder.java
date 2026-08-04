package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import org.springframework.stereotype.Component;

/**
 * Builds the prompt for {@link com.harvest.chef.reasoning.ReasoningMode#TECHNIQUE_EXPLANATION}:
 * a standalone cooking-technique or knowledge question ("how do I know when chicken is done?",
 * "why do you rest meat?"), not about a specific already-shown recipe.
 *
 * {@code groundedAnswer} is whatever {@code KnowledgeProviderManager} returned (null if no
 * cooking-knowledge provider had anything - currently always, since none is registered - see
 * {@code TechniqueAnswerComposer}). When present, the model must not contradict it. When absent,
 * the model still answers as an experienced chef using general culinary knowledge - that's
 * exactly the "explain techniques" / "chef coaching" capability this layer exists for - but the
 * system prompt is honest about the lower confidence of an ungrounded answer.
 */
@Component
public class TechniqueExplanationPromptBuilder {

    private static final String GROUNDED_SYSTEM_PROMPT = """
            You are an experienced chef answering a cooking-technique question. Harvest already has
            a grounded answer for this (given below) - your job is to deliver it the way a chef
            would explain it out loud: natural, confident, encouraging, concise. Never contradict
            the grounded answer or invent facts beyond it.
            """
            + RecipeContextFormatter.VOICE_GUIDANCE
            + """


            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            { "message": "your conversational response" }
            """;

    private static final String UNGROUNDED_SYSTEM_PROMPT = """
            You are an experienced chef answering a general cooking-technique question. Harvest has
            no specific grounded source for this yet, so answer using your own solid culinary
            knowledge - the way an experienced cook would explain it to someone at their counter.
            Natural, confident, encouraging, concise; avoid AI cliches and bullet-list-everything.

            STRICT RULES:
            - This is general technique/knowledge, not a specific recipe - never claim it comes from
              a retrieved recipe or invent recipe-specific facts (exact times, temperatures, or
              quantities tied to a dish you haven't seen) with false precision.
            - If the question is too vague or too specialized to answer responsibly with general
              knowledge, say so honestly and ask ONE short clarifying question rather than guessing.
            - Do not mention scores, algorithms, providers, or the pipeline itself.
            """
            + RecipeContextFormatter.VOICE_GUIDANCE
            + """


            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            { "message": "your conversational response" }
            """;

    public LLMPrompt build(ConversationContext context, String groundedAnswer) {
        StringBuilder prompt = new StringBuilder();
        RecipeContextFormatter.appendRecentTurns(prompt, context);
        prompt.append("User's question: ").append(context.getCurrentMessage()).append('\n');

        if (groundedAnswer != null && !groundedAnswer.isBlank()) {
            prompt.append("\nGrounded answer from Harvest's cooking-knowledge source:\n")
                    .append(groundedAnswer).append('\n');
            return new LLMPrompt(GROUNDED_SYSTEM_PROMPT, prompt.toString());
        }

        return new LLMPrompt(UNGROUNDED_SYSTEM_PROMPT, prompt.toString());
    }
}
