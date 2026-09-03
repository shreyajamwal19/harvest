package com.harvest.service;

import com.harvest.chef.pantry.repository.PantryItemRepository;
import com.harvest.chef.personalization.repository.RecipeHistoryRepository;
import com.harvest.chef.personalization.repository.SavedRecipeRepository;
import com.harvest.chef.personalization.repository.UserPreferenceRepository;
import com.harvest.chef.repository.ConversationMessageRepository;
import com.harvest.chef.repository.ConversationSessionRepository;
import com.harvest.entity.User;
import com.harvest.exception.IncorrectPasswordException;
import com.harvest.exception.ResourceNotFoundException;
import com.harvest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * There was previously no way to delete an account at all - every other piece of account
 * self-service (change password, view/forget preferences) got added this session, but deletion
 * was still missing entirely.
 *
 * None of Harvest's entities use JPA relationships/cascade for userId (every table - pantry
 * items, saved recipes, recipe history, preferences, conversation sessions and messages - just
 * stores a plain Long userId column, confirmed by reading each entity directly rather than
 * assuming). That means deleting only the User row would silently orphan every other table:
 * rows referencing a userId that no longer exists, invisible to the person who "deleted" their
 * account but still sitting in the database indefinitely. This explicitly deletes from every one
 * of those tables, in dependency order (messages before their sessions, since messages
 * reference sessionId, not userId, and would otherwise have nothing left to join through),
 * before finally deleting the User row itself.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountDeletionService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConversationMessageRepository conversationMessageRepository;
    private final ConversationSessionRepository conversationSessionRepository;
    private final PantryItemRepository pantryItemRepository;
    private final SavedRecipeRepository savedRecipeRepository;
    private final RecipeHistoryRepository recipeHistoryRepository;
    private final UserPreferenceRepository userPreferenceRepository;

    @Transactional
    public void deleteAccount(Long userId, String currentPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IncorrectPasswordException("Current password is incorrect");
        }

        int messages = conversationMessageRepository.deleteAllForUser(userId);
        int sessions = conversationSessionRepository.deleteAllByUserId(userId);
        int pantryItems = pantryItemRepository.deleteAllByUserId(userId);
        int savedRecipes = savedRecipeRepository.deleteAllByUserId(userId);
        int historyEntries = recipeHistoryRepository.deleteAllByUserId(userId);
        int preferences = userPreferenceRepository.deleteAllByUserId(userId);

        userRepository.delete(user);

        log.info("[account] deleted userId={} messages={} sessions={} pantryItems={} "
                        + "savedRecipes={} historyEntries={} preferences={}",
                userId, messages, sessions, pantryItems, savedRecipes, historyEntries, preferences);
    }
}
