package com.harvest.chef.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.repository.ConversationSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Persists lightweight per-session retrieval state - the last search query,
 * the ingredients it was built from, and which recipe titles have already
 * been shown - so a later "more" / "something else" turn can continue the
 * same search instead of starting over.
 *
 * Deliberately separate from MemoryWriteService: that stage records the
 * conversational transcript (what was said), this one records retrieval
 * state (what was searched for and shown), and the two evolve
 * independently.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SessionStateService {

    /** Bounds how many shown titles are remembered per session, so this never grows unbounded. */
    private static final int MAX_SHOWN_TITLES = 60;

    private final ConversationSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void updateAfterRecipeTurn(Long sessionId, RetrievalPlan plan, List<RecipeResponse> recipes) {
        if (plan == null) {
            return;
        }

        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setLastSearchQuery(plan.getSearchQuery());
            session.setLastMentionedIngredients(plan.getMentionedIngredients() == null
                    ? "" : String.join(",", plan.getMentionedIngredients()));

            if (recipes != null && !recipes.isEmpty()) {
                session.setShownRecipeTitles(appendShownTitles(session.getShownRecipeTitles(), recipes));
                session.setLastShownRecipesJson(serializeRecipes(recipes));
            }

            sessionRepository.save(session);
        });
    }

    private String appendShownTitles(String existingPipeSeparated, List<RecipeResponse> newRecipes) {
        Set<String> titles = new LinkedHashSet<>();
        if (existingPipeSeparated != null && !existingPipeSeparated.isBlank()) {
            for (String title : existingPipeSeparated.split("\\|")) {
                if (!title.isBlank()) {
                    titles.add(title.trim());
                }
            }
        }
        for (RecipeResponse recipe : newRecipes) {
            if (recipe.getTitle() != null && !recipe.getTitle().isBlank()) {
                titles.add(recipe.getTitle().trim().toLowerCase(Locale.ROOT));
            }
        }

        List<String> capped = new ArrayList<>(titles);
        int excess = capped.size() - MAX_SHOWN_TITLES;
        if (excess > 0) {
            capped = capped.subList(excess, capped.size()); // keep the most recently shown titles
        }
        return String.join("|", capped);
    }

    /**
     * Never lets a serialization failure break the recipe turn itself - worst case, the next
     * follow-up turn has nothing to ground against and falls back to a fresh retrieval.
     */
    private String serializeRecipes(List<RecipeResponse> recipes) {
        try {
            return objectMapper.writeValueAsString(recipes);
        } catch (Exception e) {
            log.warn("Failed to serialize shown recipes for session state: {}", e.getMessage());
            return null;
        }
    }
}
