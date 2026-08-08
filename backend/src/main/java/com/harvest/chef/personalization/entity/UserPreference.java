package com.harvest.chef.personalization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * One durable fact the personalization engine has learned about a user -
 * e.g. (FAVORITE_CUISINE, "indian", confidence=0.9, EXPLICIT). Confidence
 * is blended gradually via an exponential moving average rather than set
 * outright on every signal - see {@code UserProfileService} - so a single
 * ambiguous behavioural signal can never flip a preference on its own.
 */
@Entity
@Table(name = "user_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category", "value"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PreferenceCategory category;

    /** The learned value itself, e.g. "mushrooms", "indian", "vegetarian", "quick". Lowercased. */
    @Column(nullable = false, length = 200)
    private String value;

    /** 0.0-1.0. How confidently this preference is believed to hold. */
    @Column(nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PreferenceSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
