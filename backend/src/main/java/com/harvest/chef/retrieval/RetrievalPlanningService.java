package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RequestIntent;
import com.harvest.chef.dto.RetrievalPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Retrieval Orchestrator's planning step. Now the pipeline's entry
 * point right after Context Assembly - there is no upstream Goal
 * Reasoning stage anymore.
 *
 * Deterministic, rule-based implementation - no LLM call, so the backend
 * runs without any external API key. Intent is classified with keyword
 * heuristics; ingredients are pulled out with plain delimiter-based
 * parsing (not a curated ingredient dictionary) so this stays generic
 * parsing rather than a fixed catalog. Produces the same {@link RetrievalPlan}
 * contract the rest of the pipeline already expects.
 */
@Service
@Slf4j
public class RetrievalPlanningService {

    // Small, local keyword sets used only for rule-based classification - not a
    // separate reasoning framework, just inline heuristics for this one stage.
    private static final List<String> TECHNIQUE_KEYWORDS = List.of(
            "why", "how do i fix", "how does", "went wrong", "split", "curdled", "curdle",
            "dense", "burnt", "burn", "overcooked", "undercooked", "soggy", "tough",
            "deflated", "sunk", "sank", "rubbery", "grainy", "separated", "lumpy",
            "raw in the middle", "help my", "fix my"
    );

    private static final List<String> NUTRITION_KEYWORDS = List.of(
            "protein", "calorie", "calories", "carb", "carbs", "fat", "diabetic",
            "macro", "macros", "healthy", "low-carb", "low carb", "keto"
    );

    private static final List<String> INGREDIENT_INTELLIGENCE_KEYWORDS = List.of(
            "substitute", "substitution", "instead of", "swap", "pairs with", "pairing",
            "store", "storage", "shelf life", "how long does", "keep fresh"
    );

    // Common lead-in phrases stripped before splitting a message into ingredient tokens.
    private static final List<String> LEAD_IN_PHRASES = List.of(
            "i have", "i've got", "i have got", "i want", "i'd like", "i would like",
            "using", "with", "make something with", "cook with"
    );

    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "some", "and", "or", "to", "for", "of", "my", "me", "i",
            "please", "want", "need", "have", "got", "make", "cook", "something", "with"
    );

    public RetrievalPlan plan(ConversationContext context) {
        String message = context.getCurrentMessage() == null ? "" : context.getCurrentMessage();
        String lower = message.toLowerCase(Locale.ROOT);

        boolean isTechnique = TECHNIQUE_KEYWORDS.stream().anyMatch(lower::contains);
        List<String> ingredients = extractIngredients(lower);

        boolean needsExternalRecipes = ingredients.isEmpty();
        boolean needsNutritionGrounding = NUTRITION_KEYWORDS.stream().anyMatch(lower::contains);
        boolean needsIngredientIntelligence = INGREDIENT_INTELLIGENCE_KEYWORDS.stream().anyMatch(lower::contains);

        String searchQuery = ingredients.isEmpty() ? shortenForSearch(message) : String.join(" ", ingredients);

        String reasoningNote = "Rule-based plan: intent=" + (isTechnique ? "TECHNIQUE" : "RECIPE")
                + ", " + ingredients.size() + " ingredient token(s) parsed from the message.";

        RetrievalPlan plan = RetrievalPlan.builder()
                .intent(isTechnique ? RequestIntent.TECHNIQUE : RequestIntent.RECIPE)
                .mentionedIngredients(ingredients)
                .needsExternalRecipes(needsExternalRecipes)
                .needsNutritionGrounding(needsNutritionGrounding)
                .needsIngredientIntelligence(needsIngredientIntelligence)
                .searchQuery(searchQuery)
                .reasoningNote(reasoningNote)
                .build();

        log.info("[retrieval-planning] {}", reasoningNote);
        return plan;
    }

    /**
     * Plain delimiter-based parsing: strip common lead-in phrases, split on
     * commas/"and"/"with", drop stopwords and very short tokens. Deliberately
     * not validated against any fixed ingredient dictionary - whatever the
     * user typed as a distinct item is trusted as-is.
     */
    private List<String> extractIngredients(String lower) {
        String cleaned = lower;
        for (String phrase : LEAD_IN_PHRASES) {
            cleaned = cleaned.replace(phrase, " ");
        }

        String[] rawTokens = cleaned.split(",| and | with |\\n");
        Set<String> tokens = new LinkedHashSet<>();
        for (String raw : rawTokens) {
            String token = raw.trim().replaceAll("[.!?]+$", "");
            if (token.isEmpty()) {
                continue;
            }
            List<String> words = Arrays.stream(token.split("\\s+"))
                    .filter(w -> !STOPWORDS.contains(w))
                    .toList();
            String normalized = String.join(" ", words).trim();
            if (normalized.length() >= 2) {
                tokens.add(normalized);
            }
        }
        return new ArrayList<>(tokens);
    }

    private String shortenForSearch(String message) {
        String[] words = message.trim().split("\\s+");
        int limit = Math.min(words.length, 8);
        return String.join(" ", Arrays.copyOfRange(words, 0, limit));
    }
}
