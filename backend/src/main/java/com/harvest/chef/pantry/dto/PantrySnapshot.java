package com.harvest.chef.pantry.dto;

import com.harvest.chef.pantry.entity.PantryCategory;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only, request-scoped view of the user's pantry - loaded once per
 * turn by {@code ContextAssemblyService}, same pattern as
 * {@code UserProfileSnapshot}. Always non-null; {@link #empty()} is used
 * whenever pantry data can't be loaded, so ranking and composition code
 * never has to null-check it (see FAILURE_HANDLING: a pantry outage must
 * never break recipe recommendations).
 */
@Getter
@Builder
public class PantrySnapshot {

    @Getter
    @Builder
    public static class Item {
        private String ingredientName;
        private Double quantity;
        private String unit;
        private PantryCategory category;
        private LocalDate expiryDate;

        /** True only when an expiry date is actually stored and it's within the next 2 days. */
        public boolean isExpiringSoon() {
            if (expiryDate == null) {
                return false;
            }
            LocalDate today = LocalDate.now();
            return !expiryDate.isBefore(today) && !expiryDate.isAfter(today.plusDays(2));
        }
    }

    private Long userId;
    private List<Item> items;

    public static PantrySnapshot empty(Long userId) {
        return PantrySnapshot.builder().userId(userId).items(List.of()).build();
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    /** Normalized ingredient names only, for cheap "do I have X" containment checks. */
    public List<String> ingredientNames() {
        return items == null ? List.of() : items.stream().map(Item::getIngredientName).toList();
    }
}
