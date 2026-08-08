package com.harvest.chef.personalization.entity;

/**
 * Every kind of durable fact the personalization engine can learn about a
 * user. Deliberately closed/enumerated (not a free-text "type" string) so
 * downstream ranking code can switch on it exhaustively instead of
 * string-matching.
 */
public enum PreferenceCategory {
    FAVORITE_CUISINE,
    FAVORITE_INGREDIENT,
    DISLIKED_INGREDIENT,
    DIETARY_RESTRICTION,
    COOKING_SKILL,
    PREFERRED_COOKING_DURATION,
    PREFERRED_SERVING_SIZE,
    FAVORITE_MEAL_CATEGORY,
    HEALTH_GOAL,
    FAVORITE_APPLIANCE,
    FAVORITE_COOKING_METHOD
}
