package com.harvest.chef.dto;

import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.personalization.dto.UserProfileSnapshot;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Set;

/** Output of the Context Assembly stage. Everything downstream reads from this. */
@Getter
@Builder
public class ConversationContext {
    private Long sessionId;
    private Long userId;
    private String currentMessage;
    private List<ConversationTurn> recentTurns;
    /** The search query behind the session's last recipe request - reused on "more" turns. */
    private String lastSearchQuery;
    /** The ingredients behind the session's last recipe request - reused on "more" turns. */
    private List<String> lastMentionedIngredients;
    /** The excluded terms ("no nuts") behind the session's last recipe request - reused on "more" turns. */
    private List<String> lastExcludedIngredients;
    /** Normalized titles already shown to the user this session, so "more" doesn't repeat them. */
    private Set<String> shownRecipeTitles;
    /**
     * The full recipe(s) shown on the most recent recipe turn, deserialized from session state.
     * Empty if none yet this session. Used to ground the AI Chef Reasoning Layer's follow-up
     * handling ("make it vegetarian", "double it") without a fresh retrieval.
     */
    private List<RecipeResponse> lastShownRecipes;
    /**
     * Phase 6A - read-only snapshot of what the personalization engine currently
     * believes about this user (preferences + recent recipe history). Always
     * non-null - {@code UserProfileSnapshot.empty()} when nothing is known yet or
     * profile data couldn't be loaded, so downstream code never has to null-check it.
     */
    private UserProfileSnapshot userProfile;
    /**
     * Phase 6B - read-only snapshot of the user's pantry, loaded once per turn the same
     * way {@link #userProfile} is. Always non-null - {@code PantrySnapshot.empty()} when
     * nothing is stored yet or pantry data couldn't be loaded.
     */
    private PantrySnapshot pantry;
}
