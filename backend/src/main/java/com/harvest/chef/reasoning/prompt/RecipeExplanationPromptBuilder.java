package com.harvest.chef.reasoning.prompt;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.knowledge.model.IngredientProfile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the prompt for {@link com.harvest.chef.reasoning.ReasoningMode#RECIPE_EXPLANATION}:
 * either the initial turn right after the deterministic engine has retrieved and ranked
 * candidates, or a later follow-up asking to justify the pick ("explain why", "why did you
 * recommend this"). Both share the same system prompt and response schema - the only
 * difference is whether {@link RetrievalPlan} context (mentioned ingredients, preference tags,
 * continuation) is available to include.
 */
@Component
public class RecipeExplanationPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            You are an experienced chef sitting beside the user, reasoning about recipes a
            deterministic retrieval and scoring engine has already found and ranked. Talk like a
            chef who's genuinely looking at these options with them - never like a search engine,
            never like a generic AI assistant. Be natural, confident, and concise; avoid AI
            cliches, robotic phrasing, and excessive bullet lists - explain the way a person would.

            STRICT RULES:
            - NEVER invent a recipe, ingredient, step, or nutrition fact that is not in the data below.
            - NEVER contradict the retrieved metadata (title, ingredients, steps, servings, source).
            - Justify your recommendation: say what makes it a good fit for what they asked (time,
              who they're cooking for, what they want to avoid), not just what it is.
            - "Key terms detected in the request" may mix genuine pantry items with words that are
              actually part of a dish name (e.g. "alfredo" in "chicken alfredo"). Never tell the
              user they "already have" a term unless their own message actually says they have
              it - read their message to judge which is which.
            - You may briefly compare the given recipes and note trade-offs, but a full side-by-side
              comparison request gets its own dedicated response elsewhere - keep this focused on
              recommending and explaining.
            - Optionally, if you genuinely have one, add a single short practical aside a real chef
              would mention in passing - "this freezes well", "don't rush browning the onions",
              "prep everything before the pan gets hot". Only when it's specific to this recipe and
              actually useful, never as a stock line, and never more than one per response.
            - If "Things this user has mentioned in past conversations" is provided below, you may
              use it to sound like you know the person - but only when it's genuinely relevant to
              this recommendation, and never state it as fact about the current message.
            - If zero recipes are listed below, say so honestly. If the request suggests a nearby
              angle worth trying instead (a related ingredient, an adjacent cuisine, a broader
              category), name ONE concretely rather than just apologizing - then, if it would
              genuinely help, ask ONE short clarifying question. Never fabricate a recipe to fill
              the gap.
            - If recipes ARE listed but the request is genuinely ambiguous (e.g. just one bare
              ingredient with no clear meal type and multiple reasonable interpretations), you may
              ask ONE concise clarifying question as part of your message - but if Harvest already
              has enough to go on, don't interrogate the user, just recommend.
            - Do not mention scores, algorithms, ranking, or the pipeline itself.
            """
            + RecipeContextFormatter.VOICE_GUIDANCE
            + """


            Respond with ONLY a single JSON object, no prose, no markdown fences, matching exactly:
            {
              "responseType": "RECIPE" | "CLARIFYING_QUESTION",
              "message": "your conversational response, 1-4 sentences"
            }
            "responseType" may only be "CLARIFYING_QUESTION" when zero recipes are listed below.
            """;

    public LLMPrompt buildForInitialTurn(ConversationContext context, RetrievalPlan plan,
                                          List<RecipeResponse> rankedRecipes, List<String> userMemoryNotes,
                                          List<NutritionInfo> nutritionInfo, List<IngredientProfile> ingredientProfiles) {
        StringBuilder prompt = new StringBuilder();
        RecipeContextFormatter.appendRecentTurns(prompt, context);
        prompt.append("User's latest message: ").append(context.getCurrentMessage()).append('\n');

        if (plan.getMentionedIngredients() != null && !plan.getMentionedIngredients().isEmpty()) {
            // Deliberately NOT labeled "ingredients the user has": mentionedIngredients is every
            // content word extracted from the message, including words that are actually part
            // of a dish NAME rather than a pantry item - "chicken alfredo" extracts as
            // ["chicken", "alfredo"], and "alfredo" isn't something the user possesses, it's
            // half of the dish they're asking for. The old "has" framing produced confidently
            // wrong lines like "you already have chicken and alfredo" in real responses. This
            // neutral framing lets the model reason about it correctly using the full message
            // text above instead of being told a false possession claim as fact.
            prompt.append("Key terms detected in the request (ingredients on hand and/or dish keywords - ")
                    .append("read the user's actual message above to tell which is which): ")
                    .append(String.join(", ", plan.getMentionedIngredients())).append('\n');
        }
        if (plan.getPreferenceTags() != null && !plan.getPreferenceTags().isEmpty()) {
            prompt.append("Detected preferences: ")
                    .append(String.join(", ", plan.getPreferenceTags())).append('\n');
        }
        if (plan.getExcludedIngredients() != null && !plan.getExcludedIngredients().isEmpty()) {
            // Without this, the explanation layer has no way to know an exclusion was honored at
            // all ("no dairy", "not spicy", "without nuts") and can't acknowledge it - it only
            // ever sees the positive ingredient/preference signals above, even though the
            // deterministic engine already filtered on this.
            prompt.append("Explicitly excluded by the user (already filtered out by retrieval): ")
                    .append(String.join(", ", plan.getExcludedIngredients())).append('\n');
        }
        if (plan.isContinuation()) {
            prompt.append("This is a \"more\" turn - the user wants additional options beyond what "
                    + "was already shown.\n");
        }
        if (userMemoryNotes != null && !userMemoryNotes.isEmpty()) {
            // Things the user has said in OTHER past sessions - not this conversation. Purely
            // supplementary color for the message (e.g. noticing a repeated craving or context),
            // never a substitute for what's actually in the recipes/plan above, and never
            // grounds for claiming the user "has" an ingredient or stated a preference this turn.
            prompt.append("\nThings this user has mentioned in past conversations (context only, ")
                    .append("don't treat as true for this message unless it also appears above): \n");
            for (String note : userMemoryNotes) {
                prompt.append("- ").append(note).append('\n');
            }
        }
        RecipeContextFormatter.appendNutritionInfo(prompt, nutritionInfo);
        RecipeContextFormatter.appendIngredientProfiles(prompt, ingredientProfiles);

        prompt.append("\nRetrieved recipes (already ranked by the deterministic engine, most relevant first):\n");
        appendRecipesOrNone(prompt, rankedRecipes);
        return new LLMPrompt(SYSTEM_PROMPT, prompt.toString());
    }

    public LLMPrompt buildForExplainWhyFollowUp(ConversationContext context, List<RecipeResponse> shownRecipes) {
        StringBuilder prompt = new StringBuilder();
        RecipeContextFormatter.appendRecentTurns(prompt, context);
        prompt.append("User is asking you to explain/justify a recommendation: ")
                .append(context.getCurrentMessage()).append('\n');
        prompt.append("\nRecipe(s) already shown this session:\n");
        appendRecipesOrNone(prompt, shownRecipes);
        return new LLMPrompt(SYSTEM_PROMPT, prompt.toString());
    }

    private void appendRecipesOrNone(StringBuilder prompt, List<RecipeResponse> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            prompt.append("(none - the deterministic engine found nothing suitable)\n");
        } else {
            RecipeContextFormatter.appendRecipeBlocks(prompt, recipes);
        }
    }
}
