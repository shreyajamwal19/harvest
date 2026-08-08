package com.harvest.chef.planning.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The full deterministic output of {@code ShoppingListService}: pantry-subtracted, duplicate-
 * merged, category-grouped. Never LLM-generated - the LLM's role is limited to presenting this,
 * not deciding what's on it.
 */
@Getter
@Builder
public class ShoppingListResponse {
    private List<ShoppingListCategory> categories;
}
