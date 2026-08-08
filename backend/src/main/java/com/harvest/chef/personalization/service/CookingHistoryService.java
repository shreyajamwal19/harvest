package com.harvest.chef.personalization.service;

import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.personalization.entity.HistoryEventType;
import com.harvest.chef.personalization.entity.RecipeHistoryEntry;
import com.harvest.chef.personalization.repository.RecipeHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Persists the raw recipe-interaction log that Smart Variety (in
 * {@code RecipeScoringEngine}) and future passive-preference-inference
 * read from. The conversational pipeline currently has no explicit
 * "mark as cooked/saved/rejected" surface (Phase 6A makes no controller
 * changes), so only VIEWED and REPEATED are emitted automatically today;
 * COOKED/SAVED/REJECTED/ADAPTED are modeled and ready for a future
 * explicit action surface without another schema change.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CookingHistoryService {

    private final RecipeHistoryRepository historyRepository;

    /** Records every recipe shown to the user this turn as a VIEWED event. Never fails the turn. */
    @Transactional
    public void recordShown(Long userId, List<RecipeResponse> recipes) {
        if (userId == null || recipes == null || recipes.isEmpty()) {
            return;
        }
        try {
            for (RecipeResponse recipe : recipes) {
                if (recipe.getTitle() == null || recipe.getTitle().isBlank()) {
                    continue;
                }
                String normalizedTitle = recipe.getTitle().trim().toLowerCase(Locale.ROOT);
                HistoryEventType type = historyRepository
                        .countByUserIdAndRecipeTitleAndEventType(userId, normalizedTitle, HistoryEventType.VIEWED) > 0
                        ? HistoryEventType.REPEATED
                        : HistoryEventType.VIEWED;

                historyRepository.save(RecipeHistoryEntry.builder()
                        .userId(userId)
                        .recipeTitle(normalizedTitle)
                        .eventType(type)
                        .build());
            }
            log.info("[personalization] history recorded userId={} recipes={}", userId, recipes.size());
        } catch (Exception e) {
            // History is an enhancement, not a dependency - never let it break a recipe turn.
            log.warn("[personalization] failed to record history for userId={}: {}", userId, e.getMessage());
        }
    }
}
