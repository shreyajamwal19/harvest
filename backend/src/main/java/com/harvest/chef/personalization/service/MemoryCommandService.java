package com.harvest.chef.personalization.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.personalization.dto.UserProfileSnapshot;
import com.harvest.chef.personalization.entity.PreferenceCategory;
import com.harvest.chef.personalization.entity.PreferenceSource;
import com.harvest.chef.personalization.service.MemoryCommandDetector.MemoryCommand;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Executes a {@link MemoryCommand} deterministically - no LLM involved
 * anywhere in this class. Every branch below produces a plain, predictable
 * message built from real stored data; nothing here is ever generated or
 * paraphrased by a model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryCommandService {

    private final UserProfileService userProfileService;
    private final PreferenceLearningService preferenceLearningService;

    public ChefResponse execute(Long userId, MemoryCommand command) {
        log.info("[personalization] memory command userId={} type={} argument='{}'",
                userId, command.type(), command.argument());

        return switch (command.type()) {
            case REMEMBER_LIKE -> remember(userId, command.argument(), true);
            case REMEMBER_DISLIKE -> remember(userId, command.argument(), false);
            case REMEMBER_GENERAL -> rememberGeneral(userId, command.argument());
            case FORGET -> forget(userId, command.argument());
            case SHOW_PREFERENCES -> showPreferences(userId);
            case SHOW_HISTORY -> showHistory(userId);
            case RESET_PROFILE -> resetProfile(userId);
            case CLEAR_HISTORY -> clearHistory(userId);
        };
    }

    /**
     * Handles a "remember ..." command that wasn't phrased as a simple like/dislike -
     * "remember I am vegetarian", "remember I'm allergic to peanuts", "remember I'm trying to
     * lose weight". Reuses PreferenceLearningService's existing statement patterns rather than
     * a second copy, and - critically - only ever confirms what was ACTUALLY captured; an
     * explicit command that doesn't match any recognized statement shape is told so honestly
     * instead of a hollow "Got it," since claiming to remember something that wasn't actually
     * stored is worse than admitting the limitation.
     */
    private ChefResponse rememberGeneral(Long userId, String argument) {
        if (argument == null || argument.isBlank()) {
            return update("I didn't catch what to remember - try \"remember that I like tofu\" or "
                    + "\"remember I'm vegetarian\", for example.");
        }
        List<PreferenceLearningService.LearnedPreference> learned =
                preferenceLearningService.learnFromMessage(userProfileService, userId, argument);
        if (learned.isEmpty()) {
            return update("I couldn't tell what specifically to remember from that - try phrasing it like "
                    + "\"I'm vegetarian\", \"I don't eat shellfish\", or \"I love garlic\".");
        }
        String summary = learned.stream()
                .map(lp -> (lp.positive() ? "you like " : "you don't do ") + lp.value())
                .collect(Collectors.joining("; "));
        return update("Got it - I'll remember that " + summary + ".");
    }

    private ChefResponse remember(Long userId, String argument, boolean positive) {
        if (argument == null || argument.isBlank()) {
            return update("I didn't catch what to remember - try \"remember that I like tofu\", for example.");
        }
        // "remember I like Italian food" should learn a cuisine preference, same as saying it in
        // ordinary conversation would via PreferenceLearningService's CUISINE_PATTERN - otherwise
        // the exact same statement gets miscategorized as FAVORITE_INGREDIENT just because it
        // arrived through this command path instead. Only checked for the positive direction:
        // there's no DISLIKED_CUISINE category to file a dislike under.
        if (positive) {
            java.util.Optional<String> cuisine = preferenceLearningService.matchCuisine(argument);
            if (cuisine.isPresent()) {
                userProfileService.reinforce(userId, PreferenceCategory.FAVORITE_CUISINE, cuisine.get(),
                        PreferenceSource.EXPLICIT);
                return update("Got it - I'll remember that you like " + cuisine.get() + " food.");
            }
        }
        PreferenceCategory category = positive ? PreferenceCategory.FAVORITE_INGREDIENT
                : PreferenceCategory.DISLIKED_INGREDIENT;
        if (positive) {
            userProfileService.reinforce(userId, category, argument, PreferenceSource.EXPLICIT);
        } else {
            userProfileService.weaken(userId, category, argument, PreferenceSource.EXPLICIT);
        }
        String verb = positive ? "like" : "dislike";
        return update("Got it - I'll remember that you " + verb + " " + argument.toLowerCase() + ".");
    }

    private ChefResponse forget(Long userId, String argument) {
        if (argument == null || argument.isBlank()) {
            return update("Tell me what to forget, e.g. \"forget mushrooms\".");
        }
        int removed = userProfileService.forget(userId, argument);
        return update(removed > 0
                ? "Done - I've forgotten what I knew about \"" + argument.toLowerCase() + "\"."
                : "I didn't have anything stored about \"" + argument.toLowerCase() + "\".");
    }

    private ChefResponse showPreferences(Long userId) {
        UserProfileSnapshot snapshot = userProfileService.loadSnapshot(userId);
        if (snapshot.getPreferences() == null || snapshot.getPreferences().isEmpty()) {
            return update("I don't have any preferences saved for you yet - tell me what you like or dislike "
                    + "and I'll remember it.");
        }
        String listed = snapshot.getPreferences().stream()
                .sorted((a, b) -> Double.compare(b.getConfidence(), a.getConfidence()))
                .limit(10)
                .map(p -> "- " + p.getCategory().name().toLowerCase().replace('_', ' ') + ": " + p.getValue())
                .collect(Collectors.joining("\n"));
        return update("Here's what I know about you:\n" + listed);
    }

    private ChefResponse showHistory(Long userId) {
        UserProfileSnapshot snapshot = userProfileService.loadSnapshot(userId);
        List<String> titles = snapshot.getRecentRecipeTitles();
        if (titles == null || titles.isEmpty()) {
            return update("You haven't been shown any recipes I've logged yet.");
        }
        String listed = titles.stream().limit(10).map(t -> "- " + t).collect(Collectors.joining("\n"));
        return update("Recently: \n" + listed);
    }

    private ChefResponse resetProfile(Long userId) {
        userProfileService.resetProfile(userId);
        return update("Your profile is reset - I've cleared everything I'd learned about your preferences.");
    }

    private ChefResponse clearHistory(Long userId) {
        userProfileService.clearHistory(userId);
        return update("Cleared your cooking history.");
    }

    private ChefResponse update(String message) {
        return ChefResponse.builder()
                .type(ChefResponseType.PROFILE_UPDATE)
                .message(message)
                .recipes(null)
                .build();
    }
}
