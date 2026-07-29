package com.harvest.chef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Local Recipe Provider's data source. One provider among several in Phase 2 -
 * no longer the primary source of Chef Brain intelligence, just the fastest
 * and most reliable one to check first.
 */
@Entity
@Table(name = "recipes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Integer servings;

    @ElementCollection(fetch = FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "recipe_ingredients", joinColumns = @jakarta.persistence.JoinColumn(name = "recipe_id"))
    @Column(name = "ingredient")
    private List<String> ingredients;

    @ElementCollection(fetch = FetchType.EAGER)
    @jakarta.persistence.CollectionTable(name = "recipe_steps", joinColumns = @jakarta.persistence.JoinColumn(name = "recipe_id"))
    @Column(name = "step", columnDefinition = "TEXT")
    @jakarta.persistence.OrderColumn(name = "step_order")
    private List<String> steps;

    private String cuisine;
}
