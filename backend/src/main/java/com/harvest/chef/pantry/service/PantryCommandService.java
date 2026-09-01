package com.harvest.chef.pantry.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.pantry.service.PantryCommandDetector.PantryCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Executes a {@link PantryCommand} deterministically - no LLM involved.
 * Mirrors {@code MemoryCommandService}'s shape from Phase 6A.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PantryCommandService {

    private final PantryService pantryService;

    public ChefResponse execute(Long userId, PantryCommand command) {
        log.info("[pantry] command userId={} type={} ingredient='{}' quantity={} unit={}",
                userId, command.type(), command.ingredient(), command.quantity(), command.unit());

        return switch (command.type()) {
            case ADD -> add(userId, command);
            case REMOVE -> remove(userId, command);
            case CONSUME -> consume(userId, command);
            case SHOW -> show(userId);
            case CLEAR -> clear(userId);
        };
    }

    private ChefResponse add(Long userId, PantryCommand command) {
        if (command.ingredient() == null || command.ingredient().isBlank()) {
            return update("What did you pick up? Try \"I bought eggs\" or \"add tomatoes\".");
        }
        // Chat-based expiry parsing deliberately not attempted here - extracting a real date
        // from free text ("expires next Tuesday", "good until the 15th") risks getting it wrong
        // in a way that would silently mislead the expiry-aware recipe scoring. Expiry can be
        // set explicitly on the Pantry page instead.
        pantryService.addOrRestock(userId, command.ingredient(), command.quantity(), command.unit(), null);
        String amount = command.quantity() != null
                ? " (" + formatQuantity(command.quantity()) + (command.unit() != null ? " " + command.unit() : "") + ")"
                : "";
        return update("Added " + command.ingredient().toLowerCase() + amount + " to your pantry.");
    }

    private ChefResponse remove(Long userId, PantryCommand command) {
        if (command.ingredient() == null || command.ingredient().isBlank()) {
            return update("What should I remove? Try \"remove onions\".");
        }
        int removed = pantryService.remove(userId, command.ingredient());
        return update(removed > 0
                ? "Removed " + command.ingredient().toLowerCase() + " from your pantry."
                : "I didn't have " + command.ingredient().toLowerCase() + " in your pantry.");
    }

    private ChefResponse consume(Long userId, PantryCommand command) {
        if (command.ingredient() == null || command.ingredient().isBlank()) {
            return update("What did you run out of?");
        }
        int removed = pantryService.consume(userId, command.ingredient());
        return update(removed > 0
                ? "Got it - marked " + command.ingredient().toLowerCase() + " as used up."
                : "I didn't have " + command.ingredient().toLowerCase() + " tracked in your pantry.");
    }

    private ChefResponse show(Long userId) {
        PantrySnapshot snapshot = pantryService.loadSnapshot(userId);
        if (snapshot.isEmpty()) {
            return update("Your pantry is empty as far as I know - tell me what you've got, "
                    + "e.g. \"I have eggs, spinach, and rice\".");
        }
        String listed = snapshot.getItems().stream()
                .map(item -> "- " + item.getIngredientName()
                        + (item.getQuantity() != null ? " (" + formatQuantity(item.getQuantity())
                                + (item.getUnit() != null ? " " + item.getUnit() : "") + ")" : "")
                        + (item.isExpiringSoon() ? " - expiring soon" : ""))
                .collect(Collectors.joining("\n"));
        return update("Here's what's in your pantry:\n" + listed);
    }

    private ChefResponse clear(Long userId) {
        pantryService.clear(userId);
        return update("Cleared your pantry.");
    }

    private String formatQuantity(double quantity) {
        return quantity == Math.floor(quantity) ? String.valueOf((long) quantity) : String.valueOf(quantity);
    }

    private ChefResponse update(String message) {
        return ChefResponse.builder()
                .type(ChefResponseType.PANTRY_UPDATE)
                .message(message)
                .recipes(null)
                .build();
    }
}
