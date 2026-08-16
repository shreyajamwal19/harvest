package com.harvest.chef.personalization.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.exception.ChefReasoningException;
import com.harvest.chef.personalization.dto.SaveRecipeRequest;
import com.harvest.chef.personalization.dto.SavedRecipeResponse;
import com.harvest.chef.personalization.entity.HistoryEventType;
import com.harvest.chef.personalization.entity.SavedRecipe;
import com.harvest.chef.personalization.repository.SavedRecipeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Explicit "save a recipe" surface - the future extension point {@code CookingHistoryService}
 * and {@link HistoryEventType#SAVED} were built for but never had a caller until now. Saving
 * is idempotent per (user, normalized title): saving an already-saved recipe just refreshes
 * its stored content rather than creating a duplicate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SavedRecipeService {

    private final SavedRecipeRepository savedRecipeRepository;
    private final CookingHistoryService cookingHistoryService;
    private final ObjectMapper objectMapper;

    @Transactional
    public SavedRecipeResponse save(Long userId, SaveRecipeRequest request) {
        String normalizedTitle = request.getTitle().trim().toLowerCase(Locale.ROOT);
        RecipeResponse recipe = toRecipeResponse(request);
        String json = writeJson(recipe);

        SavedRecipe entity = savedRecipeRepository.findByUserIdAndRecipeTitle(userId, normalizedTitle)
                .orElseGet(() -> SavedRecipe.builder().userId(userId).recipeTitle(normalizedTitle).build());
        entity.setRecipeJson(json);
        SavedRecipe saved = savedRecipeRepository.save(entity);

        cookingHistoryService.recordEvent(userId, normalizedTitle, HistoryEventType.SAVED);
        log.info("[saved-recipes] saved userId={} title='{}'", userId, normalizedTitle);

        return toResponse(saved);
    }

    public List<SavedRecipeResponse> list(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return savedRecipeRepository.findByUserIdOrderBySavedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public boolean isSaved(Long userId, String recipeTitle) {
        if (userId == null || recipeTitle == null || recipeTitle.isBlank()) {
            return false;
        }
        return savedRecipeRepository.existsByUserIdAndRecipeTitle(userId, recipeTitle.trim().toLowerCase(Locale.ROOT));
    }

    @Transactional
    public boolean removeById(Long userId, Long savedRecipeId) {
        if (userId == null || savedRecipeId == null) {
            return false;
        }
        Optional<SavedRecipe> entity = savedRecipeRepository.findByIdAndUserId(savedRecipeId, userId);
        if (entity.isEmpty()) {
            return false;
        }
        savedRecipeRepository.delete(entity.get());
        return true;
    }

    private RecipeResponse toRecipeResponse(SaveRecipeRequest request) {
        return RecipeResponse.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .servings(request.getServings())
                .ingredients(request.getIngredients())
                .steps(request.getSteps())
                .notes(request.getNotes())
                .rationale(request.getRationale())
                .missingIngredients(request.getMissingIngredients())
                .source(request.getSource())
                .build();
    }

    private SavedRecipeResponse toResponse(SavedRecipe entity) {
        return SavedRecipeResponse.builder()
                .id(entity.getId())
                .savedAt(entity.getSavedAt())
                .recipe(readJson(entity.getRecipeJson()))
                .build();
    }

    private String writeJson(RecipeResponse recipe) {
        try {
            return objectMapper.writeValueAsString(recipe);
        } catch (Exception e) {
            log.error("Failed to serialize recipe for saving: {}", recipe.getTitle(), e);
            throw new ChefReasoningException("Couldn't save that recipe right now");
        }
    }

    private RecipeResponse readJson(String json) {
        try {
            return objectMapper.readValue(json, RecipeResponse.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize saved recipe JSON, skipping: {}", e.getMessage());
            return RecipeResponse.builder().title("(unavailable)").build();
        }
    }
}
