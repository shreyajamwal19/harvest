package com.harvest.chef.pantry.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognizes pantry commands ("I bought eggs", "remove onions", "I ran
 * out of rice", ...) via plain deterministic pattern matching - never the
 * LLM, per PANTRY_COMMANDS. Checked in {@code CompositionService} right
 * after Phase 6A's memory commands and before meal-plan/shopping-list/
 * recipe routing, so a pantry update can never accidentally become a
 * recipe search.
 */
@Component
public class PantryCommandDetector {

    /** A recognized command, its ingredient argument, and an optional parsed quantity/unit. */
    public record PantryCommand(PantryCommandType type, String ingredient, Double quantity, String unit) {
    }

    private static final Pattern SHOW = Pattern.compile(
            "^(?:show\\s+my\\s+pantry|what'?s\\s+in\\s+my\\s+pantry|view\\s+my\\s+pantry)[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CLEAR = Pattern.compile("^clear\\s+my\\s+pantry[.!?]*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CONSUME = Pattern.compile(
            "^i\\s+(?:used(?:\\s+up)?|ran\\s+out\\s+of|used)\\s+(?:the\\s+|my\\s+|all\\s+the\\s+)?(.+?)[.!?]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_MORE = Pattern.compile("^no\\s+more\\s+(.+?)[.!?]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern REMOVE = Pattern.compile("^remove\\s+(?:the\\s+)?(.+?)[.!?]*$", Pattern.CASE_INSENSITIVE);

    private static final Pattern BOUGHT = Pattern.compile("^i\\s+bought\\s+(.+?)[.!?]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HAVE = Pattern.compile("^i\\s+have\\s+(.+?)[.!?]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADD = Pattern.compile("^add\\s+(?:some\\s+)?(.+?)[.!?]*$", Pattern.CASE_INSENSITIVE);

    /** Leading "2", "2.5", "2 lbs", "a dozen" before the ingredient name, e.g. "2 lbs chicken". */
    private static final Pattern LEADING_QUANTITY =
            Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*([a-zA-Z]{1,10})?\\s+(.+)$");

    public Optional<PantryCommand> detect(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String trimmed = message.trim();

        if (SHOW.matcher(trimmed).matches()) {
            return Optional.of(new PantryCommand(PantryCommandType.SHOW, null, null, null));
        }
        if (CLEAR.matcher(trimmed).matches()) {
            return Optional.of(new PantryCommand(PantryCommandType.CLEAR, null, null, null));
        }

        Matcher consume = CONSUME.matcher(trimmed);
        if (consume.matches()) {
            return Optional.of(withQuantity(PantryCommandType.CONSUME, consume.group(1)));
        }

        Matcher noMore = NO_MORE.matcher(trimmed);
        if (noMore.matches()) {
            return Optional.of(withQuantity(PantryCommandType.REMOVE, noMore.group(1)));
        }
        Matcher remove = REMOVE.matcher(trimmed);
        if (remove.matches()) {
            return Optional.of(withQuantity(PantryCommandType.REMOVE, remove.group(1)));
        }

        Matcher bought = BOUGHT.matcher(trimmed);
        if (bought.matches()) {
            return Optional.of(withQuantity(PantryCommandType.ADD, bought.group(1)));
        }
        Matcher have = HAVE.matcher(trimmed);
        if (have.matches()) {
            return Optional.of(withQuantity(PantryCommandType.ADD, have.group(1)));
        }
        Matcher add = ADD.matcher(trimmed);
        if (add.matches()) {
            return Optional.of(withQuantity(PantryCommandType.ADD, add.group(1)));
        }

        return Optional.empty();
    }

    private PantryCommand withQuantity(PantryCommandType type, String argument) {
        Matcher qty = LEADING_QUANTITY.matcher(argument.trim());
        if (qty.matches()) {
            Double amount = Double.parseDouble(qty.group(1));
            String unit = qty.group(2);
            String ingredient = qty.group(3).trim();
            return new PantryCommand(type, ingredient, amount, unit);
        }
        return new PantryCommand(type, argument.trim(), null, null);
    }
}
