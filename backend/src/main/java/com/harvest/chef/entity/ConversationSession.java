package com.harvest.chef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * A single conversation thread between a user and the Chef Brain. Phase 1
 * memory is deliberately basic: this just anchors messages to a user and a
 * timeline. Durable cross-session user profile facts are a later phase.
 */
@Entity
@Table(name = "conversation_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The search query behind the most recent recipe request in this session. */
    @Column(name = "last_search_query", columnDefinition = "TEXT")
    private String lastSearchQuery;

    /** Comma-separated ingredients behind the most recent recipe request in this session. */
    @Column(name = "last_mentioned_ingredients", columnDefinition = "TEXT")
    private String lastMentionedIngredients;

    /** Pipe-separated, lowercased titles already shown this session (bounded, most-recent-kept). */
    @Column(name = "shown_recipe_titles", columnDefinition = "TEXT")
    private String shownRecipeTitles;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
