package com.harvest.chef.personalization.dto;

import com.harvest.chef.personalization.entity.PreferenceCategory;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Read-only, request-scoped view of everything the personalization engine
 * currently believes about a user - loaded once per turn by
 * {@code ContextAssemblyService} and carried on {@code ConversationContext}
 * so every downstream stage (scoring, composers, the AI Chef reasoning
 * layer) reads the same consistent snapshot instead of hitting the
 * database repeatedly. Always non-null; {@link #empty()} is used whenever
 * profile data can't be loaded, so the rest of the pipeline never has to
 * null-check it.
 */
@Getter
@Builder
public class UserProfileSnapshot {

    /** One durable, confidence-weighted fact - the in-memory twin of {@code UserPreference}. */
    @Getter
    @Builder
    public static class PreferenceSignal {
        private PreferenceCategory category;
        private String value;
        private double confidence;
        private String source;
    }

    private Long userId;
    private List<PreferenceSignal> preferences;
    /** Normalized (lowercased) titles from the user's most recent recipe history, most-recent-first. */
    private List<String> recentRecipeTitles;

    public static UserProfileSnapshot empty(Long userId) {
        return UserProfileSnapshot.builder()
                .userId(userId)
                .preferences(List.of())
                .recentRecipeTitles(List.of())
                .build();
    }

    public boolean isEmpty() {
        return (preferences == null || preferences.isEmpty())
                && (recentRecipeTitles == null || recentRecipeTitles.isEmpty());
    }
}
