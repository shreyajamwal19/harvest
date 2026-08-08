package com.harvest.chef.personalization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * One recipe interaction event for a user - the raw log that Smart Variety
 * and (future) passive-preference-inference read from. Deliberately just a
 * fact log, not itself a ranking signal: {@code RecipeScoringEngine} reads
 * recent entries and turns them into a soft ranking penalty/boost, this
 * entity never encodes a score itself.
 */
@Entity
@Table(name = "recipe_history_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Normalized (trimmed, lowercased) recipe title - matches RecipeResponse#getTitle(). */
    @Column(name = "recipe_title", nullable = false, length = 300)
    private String recipeTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private HistoryEventType eventType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
