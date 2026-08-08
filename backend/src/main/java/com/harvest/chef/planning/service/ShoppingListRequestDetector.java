package com.harvest.chef.planning.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Recognizes shopping-list requests ("generate my grocery list", "what ingredients am I
 * missing") deterministically - no LLM. What goes on the list is entirely
 * {@code ShoppingListService}'s job, sourced from whatever recipes were most recently shown
 * (a normal recipe search or a generated meal plan).
 */
@Component
public class ShoppingListRequestDetector {

    private static final Pattern TRIGGER = Pattern.compile(
            "grocery\\s*list|shopping\\s*list|what\\s+(?:ingredients\\s+)?am\\s+i\\s+missing|"
                    + "what\\s+do\\s+i\\s+(?:still\\s+)?need\\s+to\\s+buy|what\\s+.*(?:should|do)\\s+i\\s+buy",
            Pattern.CASE_INSENSITIVE);

    public boolean detect(String message) {
        return message != null && TRIGGER.matcher(message).find();
    }
}
