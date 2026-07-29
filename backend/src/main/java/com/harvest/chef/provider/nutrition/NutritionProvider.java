package com.harvest.chef.provider.nutrition;

import com.harvest.chef.dto.NutritionInfo;

import java.util.List;

/** Grounded nutrition lookups. Implementations must never invent values - return empty instead. */
public interface NutritionProvider {
    List<NutritionInfo> lookup(List<String> ingredientNames);
}
