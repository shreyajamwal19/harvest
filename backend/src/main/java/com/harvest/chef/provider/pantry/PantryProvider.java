package com.harvest.chef.provider.pantry;

import java.util.List;

/**
 * Current pantry/inventory awareness. Phase 2 derives this from what the
 * user has mentioned in conversation; a persisted, structured pantry (with
 * expiry tracking) is a natural future upgrade behind this same interface.
 */
public interface PantryProvider {
    List<String> currentPantryItems(List<String> mentionedIngredients);
}
