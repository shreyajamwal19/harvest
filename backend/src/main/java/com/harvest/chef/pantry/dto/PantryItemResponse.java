package com.harvest.chef.pantry.dto;

import com.harvest.chef.pantry.entity.PantryCategory;
import com.harvest.chef.pantry.entity.PantryItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** Public API shape for a pantry item - never exposes the JPA entity directly. */
@Getter
@Builder
@AllArgsConstructor
public class PantryItemResponse {
    private Long id;
    private String ingredientName;
    private Double quantity;
    private String unit;
    private PantryCategory category;
    private LocalDate expiryDate;
    private boolean expiringSoon;

    public static PantryItemResponse from(PantryItem item) {
        return PantryItemResponse.builder()
                .id(item.getId())
                .ingredientName(item.getIngredientName())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .category(item.getCategory())
                .expiryDate(item.getExpiryDate())
                .expiringSoon(isExpiringSoon(item.getExpiryDate()))
                .build();
    }

    /** Mirrors PantrySnapshot.Item.isExpiringSoon() so the two views never disagree. */
    private static boolean isExpiringSoon(LocalDate expiryDate) {
        if (expiryDate == null) {
            return false;
        }
        LocalDate today = LocalDate.now();
        return !expiryDate.isBefore(today) && !expiryDate.isAfter(today.plusDays(2));
    }
}
