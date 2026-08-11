package com.harvest.chef.retrieval;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic recognition of explicitly negated terms in a fresh request - "chicken pizza,
 * no mushrooms", "pasta without dairy", "I don't want nuts", "hold the onions". Without this,
 * {@link RetrievalPlanningService}'s ingredient extraction has no way to distinguish "no
 * mushrooms" from "mushrooms" - both would otherwise become a positive mentioned ingredient,
 * the opposite of what the user asked for.
 *
 * Deliberately narrow in scope: this only extracts what to exclude from a FRESH message.
 * Negation about a recipe already on the table ("no onions" as a follow-up asking to adapt a
 * shown recipe) is handled separately and correctly by {@link FollowUpIntentDetector}'s existing
 * ADAPTATION_PHRASES check, which runs first in CompositionService - this detector never
 * duplicates that path.
 */
@Component
public class NegationDetector {

    // Ordered longest-phrase-first so "i dont want" matches before a bare "dont" would.
    private static final Pattern[] NEGATION_PATTERNS = {
            Pattern.compile("\\bwithout\\s+(?:any\\s+)?([a-z][a-z ]{1,30}?)(?=[.,;!?]|\\band\\b|\\bor\\b|$)"),
            Pattern.compile("\\bno\\s+([a-z][a-z ]{1,30}?)(?=[.,;!?]|\\band\\b|\\bor\\b|$)"),
            Pattern.compile("\\bhold\\s+the\\s+([a-z][a-z ]{1,30}?)(?=[.,;!?]|\\band\\b|\\bor\\b|$)"),
            Pattern.compile("\\b(?:i\\s+)?don'?t\\s+want\\s+(?:any\\s+)?([a-z][a-z ]{1,30}?)(?=[.,;!?]|\\band\\b|\\bor\\b|$)"),
            Pattern.compile("\\bskip\\s+the\\s+([a-z][a-z ]{1,30}?)(?=[.,;!?]|\\band\\b|\\bor\\b|$)"),
            Pattern.compile("\\bnot\\s+([a-z][a-z ]{1,30}?)(?=[.,;!?]|\\band\\b|\\bor\\b|$)"),
    };

    // "no" and "not" also carry sentence-level meaning unrelated to an ingredient/tag exclusion
    // ("no thanks", "not sure", "not really") - these never get treated as a food term. Checked
    // against the FIRST WORD of the cleaned capture (not just the whole phrase), since these are
    // functional/discourse words that never legitimately lead a food-term phrase - "not sure
    // what to make", "not going to lie", "not really into it" should all be discarded entirely,
    // not partially captured as a bogus excluded ingredient like "sure what to".
    private static final Set<String> NON_FOOD_NEGATION_NOISE = Set.of(
            "thanks", "thank", "problem", "worries", "idea", "clue", "sure", "really",
            "much", "way", "big", "rush", "hurry", "going", "able", "interested", "certain",
            "positive", "in", "into", "sold", "feeling", "up", "keen", "one", "fussed",
            "bothered", "convinced", "worried", "ready", "done", "here", "today");

    // Filler/intensifier words that regularly surround the real excluded term ("hold the onions
    // please", "not too spicy", "no really spicy peppers") and would otherwise become part of
    // the captured phrase, breaking the whole-phrase containment/lookup check used downstream.
    // Stripped from BOTH ends, since intensifiers can precede or follow the target word.
    private static final Set<String> FILLER_WORDS = Set.of(
            "please", "thanks", "really", "too", "very", "actually", "though", "kindly",
            "so", "quite", "super", "extremely", "particularly", "just", "even", "also");

    /** Deterministic, LLM-free scan for explicitly excluded ingredients/tags in a fresh message. */
    public Set<String> detect(String message) {
        Set<String> excluded = new LinkedHashSet<>();
        if (message == null || message.isBlank()) {
            return excluded;
        }
        String lower = " " + message.toLowerCase(Locale.ROOT).replace("'", "") + " ";

        for (Pattern pattern : NEGATION_PATTERNS) {
            Matcher matcher = pattern.matcher(lower);
            while (matcher.find()) {
                String term = cleanTerm(matcher.group(1));
                if (term.isEmpty() || isNonFoodNoise(term)) {
                    continue;
                }
                excluded.add(term);
            }
        }
        return excluded;
    }

    /**
     * Returns the message with every negation phrase's matched span removed entirely (not
     * just the leading "no"/"not"/"without" word). Intended for callers that detect POSITIVE
     * keyword signals from raw text (e.g. preference-tag phrase matching) - without this, "not
     * spicy" would still register a positive "spicy" signal downstream, because the word
     * "spicy" is still sitting right there in the string even though the user just excluded it.
     * Stripping the whole matched phrase, not just the negation word, is what makes this
     * general rather than a fix for one specific tag: any future keyword-phrase whose trigger
     * word happens to also be a negatable food term is protected the same way automatically.
     */
    public String stripNegatedSpans(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String lower = " " + message.toLowerCase(Locale.ROOT).replace("'", "") + " ";
        for (Pattern pattern : NEGATION_PATTERNS) {
            lower = pattern.matcher(lower).replaceAll(" ");
        }
        return lower.trim();
    }

    /**
     * Strips filler/intensifier words from BOTH ends of the capture (so "too spicy" and
     * "spicy too" both reduce to "spicy"), then caps the remainder to a short, ingredient-shaped
     * length. Leading-strip is what earlier versions of this detector missed: without it, "not
     * too spicy" would exclude on the literal phrase "too spicy" - which never appears verbatim
     * in any recipe - so the negation silently did nothing.
     */
    private String cleanTerm(String rawCapture) {
        java.util.List<String> words = new java.util.ArrayList<>(
                java.util.Arrays.asList(rawCapture.trim().split("\\s+")));

        int start = 0;
        while (start < words.size() && FILLER_WORDS.contains(words.get(start))) {
            start++;
        }
        int end = words.size();
        while (end > start && FILLER_WORDS.contains(words.get(end - 1))) {
            end--;
        }
        if (start >= end) {
            return "";
        }
        List<String> trimmed = words.subList(start, Math.min(end, start + 3));
        return String.join(" ", trimmed).trim();
    }

    /**
     * A term is non-food noise if its first word is a functional/discourse word that never
     * legitimately leads a food term ("sure", "really", "going", ...) - checking only the first
     * word (rather than requiring an exact whole-phrase match) is what catches "not sure what to
     * make" and "not going to bother with a sauce", where the noise word is followed by more
     * captured filler rather than standing alone.
     */
    private boolean isNonFoodNoise(String term) {
        if (term.isEmpty()) {
            return true;
        }
        String firstWord = term.split("\\s+")[0];
        return NON_FOOD_NEGATION_NOISE.contains(firstWord) || NON_FOOD_NEGATION_NOISE.contains(term);
    }
}
