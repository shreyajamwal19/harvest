package com.harvest.chef.retrieval;

import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RequestIntent;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.util.TypoCorrectionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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
@RequiredArgsConstructor
@Slf4j
public class RetrievalPlanningService {

    private final NegationDetector negationDetector;

    // Small, local keyword sets used only for rule-based classification - not a
    // separate reasoning framework, just inline heuristics for this one stage.
    private static final List<String> TECHNIQUE_KEYWORDS = List.of(
            "why did", "why is my", "why does my", "why isnt", "why wont", "why is it",
            "how do i fix", "how does", "went wrong", "split", "curdled", "curdle",
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

    // Word-boundary-aware equivalents of LEAD_IN_PHRASES, built once. Plain String.replace()
    // does a literal substring replace with no regard for word boundaries - the single-word
    // phrase "with" is a literal substring of "without" and "within", so replacing it naively
    // turned "chicken without dairy" into "chicken  out dairy", leaving a phantom "out" token
    // that survived as a bogus extracted ingredient (it isn't in STOPWORDS). "without" is one of
    // the single most common ways a user expresses an exclusion, so this wasn't a narrow edge
    // case - it silently corrupted ingredient extraction on a large fraction of negated requests.
    private static final List<Pattern> LEAD_IN_PATTERNS = LEAD_IN_PHRASES.stream()
            .map(phrase -> Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b"))
            .toList();

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
            "other", "others", "another", "next", "help",
            // Preference/category/occasion words - real search signal, but not
            // ingredients. Captured separately via detectPreferenceTags() so
            // "healthy recipes" is understood as a dietary preference rather
            // than treating the literal word "healthy" as something to match
            // against ingredient lines.
            "healthy", "dinner", "lunch", "breakfast", "brunch", "dessert", "snack",
            "cheap", "budget", "inexpensive", "affordable", "quick", "fast", "easy",
            "simple", "beginner", "spicy", "vegetarian", "vegan", "protein", "carb",
            "carbs", "comfort", "comforting", "family", "food", "foods", "meal", "meals", "high",
            // Vague/emotional/situational phrasing that carries intent but no ingredient signal
            // of its own ("I'm tired", "surprise me", "I'm broke") - see detectPreferenceTags()
            // and PREFERENCE_KEYWORDS below for how these get mapped to real scoring signal
            // instead of being silently dropped or, worse, mis-extracted as bogus "ingredients".
            "surprise", "decide", "chefs", "choice", "whatever", "know", "idea", "mood",
            "tired", "lazy", "sick", "unwell", "hungover", "broke", "poor", "money",
            "guests", "guest", "hosting", "company", "over", "picky", "fussy", "eaters",
            "restaurant", "cozy", "craving", "crave", "cravings", "pan", "pans", "pot", "pots",
            "dish", "dishes", "wash", "washing", "hate", "almost", "barely", "hardly",
            "nothing", "prep", "prepping", "prepped", "kids", "kid", "children", "work",
            "packed", "feel", "feeling", "bothered", "effort", "cleanup", "clean-up"
    );

    // Intent/preference signals for generic and conversational requests
    // ("need dinner", "I'm hungry", "healthy recipes", "quick meal", "cheap
    // meals", "date night", ...). Each tag maps to the phrases that trigger
    // it; multi-word phrases are checked directly against the raw message
    // rather than the per-word ingredient pipeline, since they're intent
    // signals, not ingredients. Consumed by RecipeScoringEngine to align
    // rankings with what the user actually meant, not just literal keyword
    // presence in a title.
    private static final Map<String, Set<String>> PREFERENCE_KEYWORDS = Map.ofEntries(
            Map.entry("general_meal", Set.of("hungry", "starving", "what should i eat", "what can i eat", "feed me")),
            Map.entry("breakfast", Set.of("breakfast", "brunch")),
            Map.entry("lunch", Set.of("lunch")),
            Map.entry("dinner", Set.of("dinner", "supper")),
            Map.entry("dessert", Set.of("dessert", "sweet tooth")),
            Map.entry("snack", Set.of("snack", "late night snack", "late-night snack")),
            Map.entry("healthy", Set.of("healthy", "health conscious", "nutritious")),
            Map.entry("high_protein", Set.of("high protein", "high-protein", "protein packed", "protein-packed")),
            Map.entry("vegetarian", Set.of("vegetarian")),
            Map.entry("vegan", Set.of("vegan")),
            Map.entry("low_carb", Set.of("low carb", "low-carb", "keto")),
            // Note: keys are matched against the already-normalized message (see normalize()),
            // which strips apostrophes before this check ever runs - so entries are written
            // without apostrophes ("im broke", not "i'm broke") to match what's actually compared.
            Map.entry("cheap", Set.of("cheap", "budget", "inexpensive", "affordable",
                    "im broke", "no money", "tight budget", "cant afford",
                    "almost nothing", "barely anything", "hardly anything")),
            Map.entry("quick", Set.of("quick", "in a hurry", "in a rush", "one pan", "one pot",
                    "minimal cleanup", "few dishes", "hate doing dishes", "hate washing dishes",
                    "dont want to wash dishes", "only have one pan", "only one pan")),
            Map.entry("easy", Set.of("easy", "simple", "beginner", "im tired",
                    "so tired", "im lazy", "feeling lazy", "dont feel like cooking",
                    "cant be bothered", "low effort", "no effort")),
            Map.entry("comfort_food", Set.of("comfort food", "comforting", "im sick",
                    "not feeling well", "under the weather", "hungover", "something cozy", "cozy")),
            Map.entry("family", Set.of("family dinner", "family meal", "family friendly", "family-friendly",
                    "picky kids", "kids are picky", "picky eaters", "fussy kids", "fussy eaters")),
            // "date_night" is the closest existing occasion bucket in RecipeScoringEngine
            // (rewards a proper main course or dessert over a snack) - hosting guests wants the
            // same lift, so it's folded into the same tag rather than inventing a new scoring
            // dimension for what is, to the ranking engine, the same underlying signal.
            Map.entry("date_night", Set.of("date night", "cooking for guests", "have guests",
                    "guests coming", "having people over", "people coming over", "hosting",
                    "company coming", "impress")),
            Map.entry("spicy", Set.of("spicy")),
            // Phase 7 (Part 4) - two occasion phrasings explicitly called out that the existing
            // tag set didn't cover yet. "prep"/"prepping"/"prepped" stay in STOPWORDS above (so
            // they're never mis-extracted as an ingredient token) - this is the multi-word
            // phrase-level signal those same words carry, handled the same way every other
            // occasion tag already is.
            Map.entry("meal_prep", Set.of("meal prep", "meal-prep", "meal prepping",
                    "batch cooking", "batch cook", "prep for the week", "prepping for the week")),
            Map.entry("movie_night", Set.of("movie night", "movie snacks", "snack for a movie",
                    "snacks for a movie", "tv snacks"))
    );

    private Set<String> detectPreferenceTags(String lower) {
        Set<String> tags = new LinkedHashSet<>();
        for (Map.Entry<String, Set<String>> entry : PREFERENCE_KEYWORDS.entrySet()) {
            if (entry.getValue().stream().anyMatch(phrase -> containsPhrase(lower, phrase))) {
                tags.add(entry.getKey());
            }
        }
        return tags;
    }

    /**
     * Whole-word matching for single-word preference phrases (multi-word phrases already can't
     * false-positive on an embedded space the same way, so they keep the plain substring check -
     * same tradeoff RecipeCategoryClassifier/RecipeScoringEngine's own containsAsWord makes).
     * Without this, single-word tags like "easy" or "spicy" could in principle match inside an
     * unrelated longer word; word-boundary matching removes that class of false positive here
     * the same way it already was fixed in RecipeScoringEngine's keyword matching.
     */
    private boolean containsPhrase(String haystack, String phrase) {
        if (phrase.contains(" ")) {
            return haystack.contains(phrase);
        }
        return Pattern.compile("\\b" + Pattern.quote(phrase) + "\\b").matcher(haystack).find();
    }

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
            "next", "more of those", "more like that", "more ideas",
            "i dont like that one", "i dont like this one", "dont like that one",
            "dont like it", "not this one", "not that one", "show another",
            "show me another", "different recipe", "different one",
            "something similar", "similar recipe", "similar one", "similar recipes",
            "anything similar"
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
        Set<String> synonymResolved = new LinkedHashSet<>();
        List<String> ingredients = extractIngredientsWithSynonymTracking(lower, synonymResolved);

        // Phase 7 - "no mushrooms" / "without cheese" must never become a positive mentioned
        // ingredient (the opposite of what the user asked for). Detected on the raw message
        // (negation phrasing relies on word order the already-tokenized `ingredients` list
        // has lost) and any extracted ingredient token that IS one of the negated terms is
        // dropped from the positive list rather than silently searched for.
        Set<String> excludedIngredients = negationDetector.detect(message);
        if (!excludedIngredients.isEmpty()) {
            ingredients = ingredients.stream()
                    .filter(ingredient -> excludedIngredients.stream().noneMatch(excluded -> isWordIn(ingredient, excluded)))
                    .toList();
        }

        // Preference-tag phrase matching runs on a negation-stripped copy of the message, not
        // the raw `lower` - otherwise "not spicy" would still register the positive "spicy"
        // tag (the word is still sitting right there in the string) at the exact moment the
        // user asked to exclude it. This is what "not" as a constraint rather than an
        // ingredient means in practice: it has to suppress the positive signal too, not just
        // add a separate negative one.
        Set<String> preferenceTags = detectPreferenceTags(negationDetector.stripNegatedSpans(message));

        boolean needsExternalRecipes = ingredients.isEmpty();
        boolean needsNutritionGrounding = NUTRITION_KEYWORDS.stream().anyMatch(lower::contains);
        boolean needsIngredientIntelligence = INGREDIENT_INTELLIGENCE_KEYWORDS.stream().anyMatch(lower::contains);

        String searchQuery = buildSearchQuery(ingredients, preferenceTags);

        String reasoningNote = "Rule-based plan: intent=" + (isTechnique ? "TECHNIQUE" : "RECIPE")
                + ", " + ingredients.size() + " ingredient token(s), " + preferenceTags.size()
                + " preference tag(s), " + excludedIngredients.size() + " excluded term(s) parsed from the message.";

        RetrievalPlan plan = RetrievalPlan.builder()
                .intent(isTechnique ? RequestIntent.TECHNIQUE : RequestIntent.RECIPE)
                .mentionedIngredients(ingredients)
                .needsExternalRecipes(needsExternalRecipes)
                .needsNutritionGrounding(needsNutritionGrounding)
                .needsIngredientIntelligence(needsIngredientIntelligence)
                .searchQuery(searchQuery)
                .reasoningNote(reasoningNote)
                .continuation(false)
                .synonymResolvedIngredients(new ArrayList<>(synonymResolved))
                .preferenceTags(preferenceTags)
                .excludedIngredients(new ArrayList<>(excludedIngredients))
                .build();

        log.info("[retrieval-planning] {}", plan.getReasoningNote());
        return plan;
    }

    /**
     * Is {@code word} (an extracted ingredient token, possibly multi-word after synonym
     * resolution - e.g. "bell pepper") excluded by {@code phrase} (a negated term, also
     * possibly multi-word)? Checks whole-phrase equality first, then whole-word containment in
     * either direction. The either-direction check matters because either side can be the
     * multi-word one: "no bell pepper" (multi-word phrase) must remove an extracted "bell
     * pepper" token, and a bare "no onion" should also remove an extracted "green onion" token.
     * A prior version only checked single-word pieces of {@code phrase} against the whole of
     * {@code word}, so an exact multi-word match like "bell pepper" == "bell pepper" never
     * actually matched - the single most common case (the user's negated term is exactly the
     * synonym-expanded ingredient name) silently failed.
     */
    private boolean isWordIn(String word, String phrase) {
        if (word.equals(phrase)) {
            return true;
        }
        for (String piece : phrase.split("\\s+")) {
            if (piece.equals(word)) {
                return true;
            }
        }
        for (String piece : word.split("\\s+")) {
            if (piece.equals(phrase)) {
                return true;
            }
        }
        return false;
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
        List<String> lastExcluded = context.getLastExcludedIngredients() == null
                ? List.of() : context.getLastExcludedIngredients();

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
                    .synonymResolvedIngredients(List.of())
                    .preferenceTags(Set.of())
                    .excludedIngredients(lastExcluded)
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
                .synonymResolvedIngredients(List.of())
                .preferenceTags(Set.of())
                .excludedIngredients(lastExcluded)
                .build();
    }

    private String buildSearchQuery(List<String> ingredients, Set<String> preferenceTags) {
        if (!ingredients.isEmpty()) {
            return String.join(" ", ingredients);
        }
        // "general_meal" (from "I'm hungry", "starving", ...) is a scoring
        // signal, not a literal searchable term - including it here would have
        // the local provider search for the words "general meal", which
        // appear in almost no real recipe and would defeat the honest-browse
        // fallback this method exists to provide.
        List<String> searchableTags = preferenceTags.stream()
                .filter(tag -> !tag.equals("general_meal"))
                .map(tag -> tag.replace('_', ' '))
                .toList();
        if (!searchableTags.isEmpty()) {
            return String.join(" ", searchableTags);
        }
        // No ingredient and no recognizable preference signal - deliberately
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
        // Exact match for the whole (cleaned) message first - covers bare single-word turns
        // ("more", "another", "next", "others") where a substring check would be dangerously
        // broad (e.g. "I need another cup of rice" contains "another" but isn't a continuation).
        if (CONTINUATION_PHRASES.contains(cleaned)) {
            return true;
        }
        // Multi-word phrases are distinctive enough to safely substring-match anywhere in the
        // message, so a real turn with extra words around them - "something else please",
        // "no, not that one" - is still recognized instead of only the bare phrase matching.
        boolean matchesDistinctivePhrase = CONTINUATION_PHRASES.stream()
                .filter(phrase -> phrase.indexOf(' ') >= 0)
                .anyMatch(cleaned::contains);
        if (matchesDistinctivePhrase) {
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
     *
     * @param synonymResolvedOut if non-null, populated with the subset of the
     *                           returned tokens that came from synonym
     *                           resolution rather than being typed exactly.
     */
    private List<String> extractIngredientsWithSynonymTracking(String lower, Set<String> synonymResolvedOut) {
        String cleaned = lower;
        for (Pattern pattern : LEAD_IN_PATTERNS) {
            cleaned = pattern.matcher(cleaned).replaceAll(" ");
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
            if (synonymResolvedOut != null && !resolved.equals(corrected)) {
                synonymResolvedOut.add(resolved);
            }
        }
        return new ArrayList<>(tokens);
    }
}
