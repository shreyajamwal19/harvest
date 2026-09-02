/** Matches backend PreferenceCategory exactly - shared by the Preferences page. */
export const PREFERENCE_CATEGORY_META = {
  FAVORITE_CUISINE: { label: 'Favorite cuisines' },
  FAVORITE_INGREDIENT: { label: 'Favorite ingredients' },
  DISLIKED_INGREDIENT: { label: 'Disliked ingredients' },
  DIETARY_RESTRICTION: { label: 'Dietary restrictions' },
  COOKING_SKILL: { label: 'Cooking skill' },
  PREFERRED_COOKING_DURATION: { label: 'Cooking duration' },
  PREFERRED_SERVING_SIZE: { label: 'Serving size' },
  FAVORITE_MEAL_CATEGORY: { label: 'Favorite meal types' },
  HEALTH_GOAL: { label: 'Health goals' },
  FAVORITE_APPLIANCE: { label: 'Favorite appliances' },
  FAVORITE_COOKING_METHOD: { label: 'Favorite cooking methods' },
}

export function preferenceCategoryLabel(category) {
  return PREFERENCE_CATEGORY_META[category]?.label
    || category.toLowerCase().replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase())
}
