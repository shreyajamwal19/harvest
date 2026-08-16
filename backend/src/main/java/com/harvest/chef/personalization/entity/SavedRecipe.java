package com.harvest.chef.personalization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * A recipe a user has explicitly chosen to keep, distinct from the passive
 * {@link RecipeHistoryEntry} view/repeat log. Stores the full recipe as JSON (same
 * serialization ConversationSession already uses for lastShownRecipesJson) rather than
 * normalized ingredient/step tables, since it's a read-mostly snapshot of what was shown
 * at save time, not something the pantry/scoring engine needs to query into.
 */
@Entity
@Table(name = "saved_recipes", uniqueConstraints =
        @UniqueConstraint(name = "uk_saved_recipe_user_title", columnNames = {"user_id", "recipe_title"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Normalized (trimmed, lowercased) - matches RecipeHistoryEntry's convention, used for dedupe. */
    @Column(name = "recipe_title", nullable = false, length = 300)
    private String recipeTitle;

    @Column(name = "recipe_json", nullable = false, columnDefinition = "TEXT")
    private String recipeJson;

    @CreationTimestamp
    @Column(name = "saved_at", nullable = false, updatable = false)
    private Instant savedAt;
}
