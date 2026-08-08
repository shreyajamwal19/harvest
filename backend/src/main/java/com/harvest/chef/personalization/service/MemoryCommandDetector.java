package com.harvest.chef.personalization.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes direct memory commands ("remember that I like...", "forget
 * mushrooms", "show my preferences", ...) via plain deterministic pattern
 * matching. Never calls the LLM - a command's execution must be exactly
 * predictable, not a language model's best guess. Checked before any
 * retrieval/recipe planning so a profile question can never accidentally
 * become a recipe search.
 */
@Component
public class MemoryCommandDetector {

    /** A recognized command plus its captured argument (may be blank for argument-less commands). */
    public record MemoryCommand(MemoryCommandType type, String argument) {
    }

    private static final Pattern REMEMBER_LIKE =
            Pattern.compile("^remember\\s+(?:that\\s+)?i\\s+(?:like|love)\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMEMBER_DISLIKE =
            Pattern.compile("^remember\\s+(?:that\\s+)?i\\s+(?:hate|dislike|don'?t like)\\s+(.+)$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern FORGET =
            Pattern.compile("^forget\\s+(?:that\\s+)?(?:i\\s+(?:like|love|hate|dislike)\\s+)?(.+)$",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_PREFERENCES = Pattern.compile(
            "^(?:show\\s+my\\s+preferences|what\\s+do\\s+you\\s+know\\s+about\\s+me|what\\s+are\\s+my\\s+preferences|update\\s+my\\s+preferences)[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHOW_HISTORY = Pattern.compile(
            "^(?:what\\s+have\\s+i\\s+cooked(?:\\s+recently)?|show\\s+my\\s+(?:cooking\\s+)?history)[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RESET_PROFILE = Pattern.compile(
            "^(?:reset\\s+my\\s+profile|delete\\s+my\\s+preferences|clear\\s+my\\s+preferences)[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAR_HISTORY = Pattern.compile(
            "^clear\\s+my\\s+(?:cooking\\s+)?history[.!?]*$", Pattern.CASE_INSENSITIVE);

    public Optional<MemoryCommand> detect(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String trimmed = message.trim();

        Matcher remLike = REMEMBER_LIKE.matcher(trimmed);
        if (remLike.matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.REMEMBER_LIKE, clean(remLike.group(1))));
        }
        Matcher remDislike = REMEMBER_DISLIKE.matcher(trimmed);
        if (remDislike.matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.REMEMBER_DISLIKE, clean(remDislike.group(1))));
        }
        if (RESET_PROFILE.matcher(trimmed).matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.RESET_PROFILE, ""));
        }
        if (CLEAR_HISTORY.matcher(trimmed).matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.CLEAR_HISTORY, ""));
        }
        if (SHOW_HISTORY.matcher(trimmed).matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.SHOW_HISTORY, ""));
        }
        if (SHOW_PREFERENCES.matcher(trimmed).matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.SHOW_PREFERENCES, ""));
        }
        Matcher forget = FORGET.matcher(trimmed);
        if (forget.matches()) {
            return Optional.of(new MemoryCommand(MemoryCommandType.FORGET, clean(forget.group(1))));
        }

        return Optional.empty();
    }

    private String clean(String argument) {
        return argument.replaceAll("[.!?]+$", "").trim();
    }
}
