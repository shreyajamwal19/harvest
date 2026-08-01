package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RequestIntent;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.util.TypoCorrectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Retrieval Orchestrator's planning step. The pipeline's entry point
 * right after Context Assembly - there is no upstream Goal Reasoning
 * stage.
 *
 * Deterministic, rule-based implementation - no LLM call, so the backend
 * runs without any external API key. Phase 4 hardens this stage against
 * real-world phrasing against the 231k-row imported recipe catalog:
 * - typo tolerance (recpues/receipes/engredients/tomatos/potatos/...)
 * - synonym resolution (capsicum/aubergine/garbanzo beans -> the words the
 *   imported dataset's ingredient lines actually use)
 * - conversational requests with no explicit ingredient ("I'm hungry",
 *   "quick breakfast", "give me healthy recipes")
 * - "more" / "anything else" continuation of a prior recipe list, reusing
 *   the session's last search instead of starting over
 * - a much broader stopword/lead-in vocabulary so filler words in casual
 *   phrasing ("what can I make with eggs", "i need egg recipes") don't
 *   leak into the extracted ingredient tokens
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

    // Common lead-in phrases stripped before splitting a message into ingredient
    // tokens. Longer/more specific phrases first so they match before their
    // shorter substrings would (map iteration below preserves insertion order).
    private static final List<String> LEAD_IN_PHRASES = List.of(
            "what can i make with", "what can you make with", "what can i cook with",
            "what should i make with", "recipes using", "recipes with", "recipe with",
            "i have got", "ive got", "i would like", "id like", "i have", "i want",
            "give me", "show me", "make something with", "cook with", "using", "with"
    );

    // Words that carry no search signal on their own - conversational filler,
    // pronouns, and generic verbs that would otherwise pollute the extracted
    // ingredient token list ("what can u make" -> without this list "u"/"can"/
    // "make" would themselves become bogus "ingredients").
    private static final Set<String> STOPWORDS = Set.of(
            "a", "an", "the", "some", "any", "and", "or", "to", "for", "of", "my", "me", "i",
            "im", "id", "ive", "youre", "dont", "cant", "whats", "please", "want", "need",
            "have", "got", "make", "makes", "cook", "cooking", "something", "with", "what",
            "can", "could", "would", "should", "u", "you", "your", "give", "show", "tell",
            "find", "get", "do", "does", "is", "are", "there", "like", "only", "just",
            "that", "this", "from", "today", "tonight", "now", "more", "options",
            "option", "recipe", "recipes", "ingredient", "ingredients", "hungry", "starving",
            "nooo", "no", "yes", "ok", "okay", "else", "anything", "different",
            "other", "others", "another", "next", "help"
    );

    // Recognized meal/mood/style category words - kept OUT of the stopword list
    // deliberately, since these carry real search signal for conversational
    // requests that have no explicit ingredient ("quick breakfast", "something
    // spicy", "give me healthy recipes").
    private static final Set<String> CATEGORY_WORDS = Set.of(
            "breakfast", "lunch", "dinner", "brunch", "snack", "dessert", "appetizer",
            "healthy", "quick", "easy", "spicy", "sweet", "savory", "vegetarian", "vegan",
            "gluten", "keto", "italian", "mexican", "indian", "chinese", "thai"
    );

    // Regional/synonym ingredient names mapped to the word the imported
    // Food.com dataset's ingredient lines are far more likely to actually
    // contain, so retrieval isn't defeated by a valid but less-common name.
    private static final java.util.Map<String, String> SYNONYMS = java.util.Map.ofEntries(
            java.util.Map.entry("capsicum", "bell pepper"),
            java.util.Map.entry("aubergine", "eggplant"),
            java.util.Map.entry("courgette", "zucchini"),
            java.util.Map.entry("garbanzo", "chickpea"),
            java.util.Map.entry("garbanzos", "chickpeas"),
            java.util.Map.entry("coriander", "cilantro"),
            java.util.Map.entry("scallion", "green onion"),
            java.util.Map.entry("scallions", "green onions"),
            java.util.Map.entry("rocket", "arugula"),
            java.util.Map.entry("beetroot", "beet"),
            java.util.Map.entry("prawns", "shrimp"),
            java.util.Map.entry("prawn", "shrimp"),
            java.util.Map.entry("maize", "corn")
    );

    // Phrases (after normalization) that mean "continue the previous list",
    // not "start a brand-new search".
    private static final Set<String> CONTINUATION_PHRASES = Set.of(
            "more", "more recipes", "more options", "more please", "show more",
            "give me more", "any more", "anything else", "what else", "something else",
            "others", "other options", "different options", "another", "another one",
            "next", "more of those", "more like that", "more ideas"
    );

    public RetrievalPlan plan(ConversationContext context) {
        String message = context.getCurrentMessage() == null ? "" : context.getCurrentMessage();
        String lower = normalize(message);

        boolean continuation = looksLikeContinuation(lower);

        if (continuation) {
            RetrievalPlan plan = buildContinuationPlan(context);
            log.info("[retrieval-planning] {}", plan.getReasoningNote());
            return plan;
        }

        boolean isTechnique = TECHNIQUE_KEYWORDS.stream().anyMatch(lower::contains);
        List<String> ingredients = extractIngredients(lower);

        boolean needsExternalRecipes = ingredients.isEmpty();
        boolean needsNutritionGrounding = NUTRITION_KEYWORDS.stream().anyMatch(lower::contains);
        boolean needsIngredientIntelligence = INGREDIENT_INTELLIGENCE_KEYWORDS.stream().anyMatch(lower::contains);

        String searchQuery = buildSearchQuery(ingredients, lower);

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
                .continuation(false)
                .build();

        log.info("[retrieval-planning] {}", plan.getReasoningNote());
        return plan;
    }

    /**
     * "More" / "anything else" turns reuse the previous search rather than
     * treating a near-empty message as a fresh (and inevitably poor)
     * query. Falls back to treating it as a normal (if vague) fresh
     * request if there's nothing to reuse - e.g. "more" as the very first
     * message in a brand-new session.
     */
    private RetrievalPlan buildContinuationPlan(ConversationContext context) {
        List<String> lastIngredients = context.getLastMentionedIngredients();
        String lastQuery = context.getLastSearchQuery();

        boolean hasPriorContext = (lastIngredients != null && !lastIngredients.isEmpty())
                || (lastQuery != null && !lastQuery.isBlank());

        if (!hasPriorContext) {
            return RetrievalPlan.builder()
                    .intent(RequestIntent.RECIPE)
                    .mentionedIngredients(List.of())
                    .needsExternalRecipes(true)
                    .needsNutritionGrounding(false)
                    .needsIngredientIntelligence(false)
                    .searchQuery("")
                    .reasoningNote("Continuation phrase with no prior search in this session - "
                            + "falling back to a broad browse.")
                    .continuation(false)
                    .build();
        }

        List<String> reusedIngredients = lastIngredients == null ? List.of() : lastIngredients;
        return RetrievalPlan.builder()
                .intent(RequestIntent.RECIPE)
                .mentionedIngredients(reusedIngredients)
                .needsExternalRecipes(reusedIngredients.isEmpty())
                .needsNutritionGrounding(false)
                .needsIngredientIntelligence(false)
                .searchQuery(lastQuery == null ? "" : lastQuery)
                .reasoningNote("Continuation request - reusing the previous search "
                        + "(\"" + lastQuery + "\") and excluding already-shown recipes.")
                .continuation(true)
                .build();
    }

    private String buildSearchQuery(List<String> ingredients, String lower) {
        if (!ingredients.isEmpty()) {
            return String.join(" ", ingredients);
        }
        List<String> categoryTokens = CATEGORY_WORDS.stream().filter(lower::contains).toList();
        if (!categoryTokens.isEmpty()) {
            return String.join(" ", categoryTokens);
        }
        // No ingredient and no recognizable category signal - deliberately
        // return blank rather than a near-meaningless literal phrase like
        // "i'm hungry"; the local provider treats a blank query as an honest
        // browse of the catalog instead of a search that would match nothing.
        return "";
    }

    private String normalize(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        // Contractions collapse to a single word ("i'm" -> "im") so they can be
        // stopword-filtered as whole tokens instead of splitting into stray
        // single letters.
        return lower.replace("'", "");
    }

    private boolean looksLikeContinuation(String normalizedLower) {
        String cleaned = normalizedLower.replaceAll("[^a-z ]", " ").replaceAll("\\s+", " ").trim();
        if (cleaned.isEmpty()) {
            return false;
        }
        if (CONTINUATION_PHRASES.contains(cleaned)) {
            return true;
        }
        String[] words = cleaned.split(" ");
        boolean shortMessage = words.length <= 4;
        boolean mentionsMoreOrAnother = cleaned.contains("more") || cleaned.contains("another");
        return shortMessage && mentionsMoreOrAnother;
    }

    /**
     * Delimiter-based parsing with typo correction, synonym resolution, and
     * a much broader stopword/lead-in vocabulary than a naive
     * implementation: strips common lead-in phrases, then splits on both
     * punctuation/conjunctions ("," / "and" / "with") AND plain whitespace,
     * so a bare space-separated list like "chicken rice garlic" - which has
     * no comma or "and" to split on - still becomes three separate
     * ingredient tokens rather than one meaningless combined phrase that
     * would never match a real ingredient line.
     *
     * Each surviving word is independently typo-corrected and
     * synonym-resolved (capsicum -> bell pepper, garbanzo -> chickpea,
     * ...) and added as its own token. A synonym that itself expands to a
     * multi-word name (e.g. "capsicum" -> "bell pepper") stays together
     * automatically, since the resolution happens before the word is added
     * to the result - it's still one logical ingredient, just expressed
     * with two words.
     *
     * Deliberately not validated against a fixed ingredient dictionary
     * beyond that - whatever distinct word survives filtering is trusted
     * as-is, so uncommon (but real) ingredients still pass through.
     */
    private List<String> extractIngredients(String lower) {
        String cleaned = lower;
        for (String phrase : LEAD_IN_PHRASES) {
            cleaned = cleaned.replace(phrase, " ");
        }
        cleaned = cleaned.replaceAll("[.!?,]", " ");

        Set<String> tokens = new LinkedHashSet<>();
        for (String word : cleaned.split("\\s+")) {
            String trimmed = word.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            // Check the raw word against stopwords BEFORE typo-correcting it -
            // otherwise a common filler word (e.g. "need") could accidentally
            // land within edit-distance of an unrelated real ingredient (e.g.
            // "beef") and get "corrected" into a bogus extracted ingredient.
            // Only words that survive as real content get the fuzzy treatment.
            if (STOPWORDS.contains(trimmed)) {
                continue;
            }
            String corrected = TypoCorrectionUtil.correct(trimmed);
            String resolved = SYNONYMS.getOrDefault(corrected, corrected);
            if (resolved.length() < 2 || STOPWORDS.contains(resolved)) {
                continue;
            }
            tokens.add(resolved);
        }
        return new ArrayList<>(tokens);
    }
}
