import { Carrot, Apple, Beef, Milk, Wheat, Snowflake, Flame, Package } from 'lucide-react'

/** Matches backend PantryCategory exactly - shared by shopping-list grouping and the pantry page. */
export const CATEGORY_META = {
  VEGETABLE: { label: 'Vegetables', icon: Carrot },
  FRUIT: { label: 'Fruit', icon: Apple },
  PROTEIN: { label: 'Protein', icon: Beef },
  DAIRY: { label: 'Dairy', icon: Milk },
  PANTRY_STAPLE: { label: 'Pantry Staples', icon: Wheat },
  FROZEN: { label: 'Frozen', icon: Snowflake },
  SPICE: { label: 'Spices & Seasonings', icon: Flame },
  OTHER: { label: 'Other', icon: Package },
}

export const CATEGORY_ORDER = Object.keys(CATEGORY_META)
