package com.harvest.chef.personalization.service;

import com.harvest.chef.personalization.dto.UserProfileSnapshot;
import com.harvest.chef.personalization.entity.PreferenceCategory;
import com.harvest.chef.personalization.entity.PreferenceSource;
import com.harvest.chef.personalization.entity.RecipeHistoryEntry;
import com.harvest.chef.personalization.entity.UserPreference;
import com.harvest.chef.personalization.repository.RecipeHistoryRepository;
import com.harvest.chef.personalization.repository.UserPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * Owns the deterministic user profile: loading a read-only snapshot for a
 * turn, and blending new signals into stored preferences via a slow
 * exponential moving average so confidence never jumps on a single
 * ambiguous data point. This is the ONLY place preference confidence math
 * happens - {@code PreferenceLearningService} and {@code CookingHistoryService}
 * both funnel through here rather than writing confidence values themselves.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    /** How much a single new signal moves confidence toward its target. Deliberately small. */
    private static final double LEARNING_RATE = 0.1;
    // An explicit contradiction ("I can't eat X anymore" after previously "I love X") needs to
    // move confidence in the OLD, opposite-polarity preference down fast, not decay it at the
    // same slow rate ordinary reinforcement uses - a stale strong preference should not keep
    // outweighing a new explicit statement for many turns. Still not an instant jump to 0 (an
    // EMA, same mechanism as everything else here), just a much larger step.
    private static final double CONTRADICTION_LEARNING_RATE = 0.7;
    private static final double EXPLICIT_TARGET_POSITIVE = 0.95;
    private static final double INFERRED_TARGET_POSITIVE = 0.75;
    private static final double MIN_CONFIDENCE = 0.05;
    private static final double MAX_CONFIDENCE = 0.99;
    private static final int RECENT_HISTORY_LIMIT = 20;

    /** category -> the category holding its opposite-polarity preference for the same value. */
    private static final java.util.Map<PreferenceCategory, PreferenceCategory> OPPOSITE_CATEGORY = java.util.Map.of(
            PreferenceCategory.DISLIKED_INGREDIENT, PreferenceCategory.FAVORITE_INGREDIENT,
            PreferenceCategory.FAVORITE_INGREDIENT, PreferenceCategory.DISLIKED_INGREDIENT);

    private final UserPreferenceRepository preferenceRepository;
    private final RecipeHistoryRepository historyRepository;

    /**
     * Never throws - a personalization outage must never break recipe
     * recommendations. On any failure this returns an empty snapshot and
     * the rest of the pipeline behaves exactly as it did before Phase 6A.
     */
    public UserProfileSnapshot loadSnapshot(Long userId) {
        if (userId == null) {
            return UserProfileSnapshot.empty(null);
        }
        try {
            List<UserPreference> preferences = preferenceRepository.findByUserIdOrderByConfidenceDesc(userId);
            List<RecipeHistoryEntry> history = historyRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);

            List<UserProfileSnapshot.PreferenceSignal> signals = preferences.stream()
                    .map(p -> UserProfileSnapshot.PreferenceSignal.builder()
                            .category(p.getCategory())
                            .value(p.getValue())
                            .confidence(p.getConfidence())
                            .source(p.getSource().name())
                            .build())
                    .toList();

            List<String> recentTitles = history.stream()
                    .map(RecipeHistoryEntry::getRecipeTitle)
                    .distinct()
                    .limit(RECENT_HISTORY_LIMIT)
                    .toList();

            log.info("[personalization] profile read userId={} preferences={} recentTitles={}",
                    userId, signals.size(), recentTitles.size());

            return UserProfileSnapshot.builder()
                    .userId(userId)
                    .preferences(signals)
                    .recentRecipeTitles(recentTitles)
                    .build();
        } catch (Exception e) {
            log.warn("[personalization] failed to load profile for userId={} - continuing without it: {}",
                    userId, e.getMessage());
            return UserProfileSnapshot.empty(userId);
        }
    }

    /**
     * Blends a new positive signal for (category, value) into the stored
     * preference. EXPLICIT statements pull confidence toward a high target
     * fast-ish (still EMA, never an instant jump to 1.0); INFERRED signals
     * pull toward a lower target, slowly.
     */
    @Transactional
    public void reinforce(Long userId, PreferenceCategory category, String value, PreferenceSource source) {
        upsert(userId, category, value, source,
                source == PreferenceSource.EXPLICIT ? EXPLICIT_TARGET_POSITIVE : INFERRED_TARGET_POSITIVE,
                LEARNING_RATE);
    }

    /** Blends a negative/contradicting signal - confidence decays toward zero, never deleted outright. */
    @Transactional
    public void weaken(Long userId, PreferenceCategory category, String value, PreferenceSource source) {
        upsert(userId, category, value, source, 0.0, LEARNING_RATE);
    }

    /**
     * Like {@link #weaken}, but for a statement strong enough to actively contradict an existing
     * opposite-polarity preference for the same value (e.g. a newly stated "I can't eat X
     * anymore" against a previously stored "loves X") - so the new signal wins quickly rather
     * than needing many repeated statements to overcome a stale, previously-confident preference.
     * Only meaningful for EXPLICIT statements; PreferenceLearningService never sets this flag for
     * merely-inferred/behavioral signals.
     */
    @Transactional
    public void weakenAsContradiction(Long userId, PreferenceCategory category, String value,
                                       PreferenceSource source) {
        upsert(userId, category, value, source, 0.0, CONTRADICTION_LEARNING_RATE);

        PreferenceCategory opposite = OPPOSITE_CATEGORY.get(category);
        if (opposite != null && userId != null && value != null && !value.isBlank()) {
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            preferenceRepository.findByUserIdAndCategoryAndValue(userId, opposite, normalized)
                    .ifPresent(existing -> {
                        double previous = existing.getConfidence();
                        double blended = Math.max(MIN_CONFIDENCE,
                                previous + CONTRADICTION_LEARNING_RATE * (0.0 - previous));
                        existing.setConfidence(blended);
                        preferenceRepository.save(existing);
                        log.info("[personalization] contradiction override userId={} category={} value='{}' "
                                        + "{}->{} (opposing new statement in category={})",
                                userId, opposite, normalized, round(previous), round(blended), category);
                    });
        }
    }

    private void upsert(Long userId, PreferenceCategory category, String value, PreferenceSource source,
                         double target, double learningRate) {
        if (userId == null || category == null || value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);

        UserPreference preference = preferenceRepository
                .findByUserIdAndCategoryAndValue(userId, category, normalized)
                .orElseGet(() -> UserPreference.builder()
                        .userId(userId)
                        .category(category)
                        .value(normalized)
                        .confidence(initialConfidence(category, source, target))
                        .source(source)
                        .build());

        double previous = preference.getConfidence();
        double blended = previous + learningRate * (target - previous);
        blended = Math.max(MIN_CONFIDENCE, Math.min(MAX_CONFIDENCE, blended));

        preference.setConfidence(blended);
        // An explicit statement always upgrades the recorded source; an inferred signal never
        // downgrades an already-explicit preference.
        if (source == PreferenceSource.EXPLICIT) {
            preference.setSource(PreferenceSource.EXPLICIT);
        }
        preferenceRepository.save(preference);

        log.info("[personalization] preference updated userId={} category={} value='{}' {}->{} source={}",
                userId, category, normalized, round(previous), round(blended), preference.getSource());
    }

    @Transactional
    public int forget(Long userId, String valueFragment) {
        if (userId == null || valueFragment == null || valueFragment.isBlank()) {
            return 0;
        }
        int removed = preferenceRepository.deleteByUserIdAndValueMatching(userId,
                valueFragment.trim().toLowerCase(Locale.ROOT));
        log.info("[personalization] forget userId={} fragment='{}' removed={}", userId, valueFragment, removed);
        return removed;
    }

    @Transactional
    public void resetProfile(Long userId) {
        int removed = preferenceRepository.deleteAllByUserId(userId);
        log.info("[personalization] profile reset userId={} preferencesRemoved={}", userId, removed);
    }

    @Transactional
    public void clearHistory(Long userId) {
        int removed = historyRepository.deleteAllByUserId(userId);
        log.info("[personalization] history cleared userId={} entriesRemoved={}", userId, removed);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Seeds a brand-new preference's starting confidence. Most categories start neutral (0.5)
     * and earn confidence gradually via the EMA in {@link #upsert} - a single "I love garlic"
     * shouldn't instantly dominate ranking (Ω-2 Part 5). A DIETARY_RESTRICTION stated explicitly
     * is different in kind: "I'm vegetarian" is a declarative fact, not a graded taste signal
     * that legitimately benefits from repeated confirmation, and understating it while it slowly
     * ramps up is a worse failure mode than a taste preference ramping up slowly. So an explicit
     * dietary restriction starts already near its target rather than at neutral.
     */
    private double initialConfidence(PreferenceCategory category, PreferenceSource source, double target) {
        if (category == PreferenceCategory.DIETARY_RESTRICTION && source == PreferenceSource.EXPLICIT) {
            return target;
        }
        return 0.5;
    }
}
