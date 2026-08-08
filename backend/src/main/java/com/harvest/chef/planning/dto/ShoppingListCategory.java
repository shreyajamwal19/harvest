package com.harvest.chef.planning.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** One grouped section of a generated shopping list, e.g. "VEGETABLE" -> ["onions", "tomatoes"]. */
@Getter
@Builder
public class ShoppingListCategory {
    private String category;
    private List<String> items;
}
