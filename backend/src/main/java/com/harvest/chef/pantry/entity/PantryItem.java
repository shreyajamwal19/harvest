package com.harvest.chef.pantry.entity;

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
import java.time.LocalDate;

/**
 * One ingredient the user has told Harvest they currently have. Quantity
 * and expiry are optional - "I have chicken" is a perfectly valid pantry
 * entry with neither; Harvest never fabricates either value when the user
 * didn't state it (see PANTRY_SYSTEM / EXPIRY_AWARENESS constraints).
 */
@Entity
@Table(name = "pantry_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "ingredient_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PantryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Normalized (trimmed, lowercased, singular-ish) ingredient name, e.g. "egg", "onion". */
    @Column(name = "ingredient_name", nullable = false, length = 200)
    private String ingredientName;

    /** Null when the user never stated an amount - "I have chicken" vs. "I bought 2 lbs chicken". */
    private Double quantity;

    @Column(length = 30)
    private String unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PantryCategory category;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    /** Null unless the user (or, in future, a receipt/barcode integration) actually provided one. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
