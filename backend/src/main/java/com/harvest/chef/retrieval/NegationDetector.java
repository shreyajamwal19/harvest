package com.harvest.chef.retrieval;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
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
    // ("no thanks", "not sure", "not really") - these never get treated as a food term.
    private static final Set<String> NON_FOOD_NEGATION_NOISE = Set.of(
            "thanks", "thank you", "problem", "worries", "idea", "clue", "sure", "really",
            "much", "way", "sure thing", "big deal", "rush", "hurry");

    // Trailing filler that regularly tags along after the real excluded term ("hold the onions
    // please") and would otherwise become part of the captured phrase, breaking the whole-phrase
    // containment check used against a candidate's ingredient text.
    private static final Set<String> TRAILING_FILLER = Set.of(
            "please", "thanks", "really", "too", "very", "actually", "though", "kindly");

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
                if (term.isEmpty() || NON_FOOD_NEGATION_NOISE.contains(term)) {
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

    /** Strips trailing filler words and caps the phrase to a short, ingredient-shaped length. */
    private String cleanTerm(String rawCapture) {
        String[] words = rawCapture.trim().split("\\s+");
        int end = words.length;
        while (end > 0 && TRAILING_FILLER.contains(words[end - 1])) {
            end--;
        }
        end = Math.min(end, 3);
        return String.join(" ", java.util.Arrays.asList(words).subList(0, end)).trim();
    }
}
