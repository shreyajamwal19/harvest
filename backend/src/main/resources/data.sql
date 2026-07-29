-- Seed data for the Local Recipe Provider (Phase 2). This is intentionally a
-- small, real starter set - Harvest's intelligence no longer depends on this
-- table's size; it's one provider among several the Retrieval Orchestrator
-- can call. Runs on every startup because ids are pinned and guarded by
-- NOT EXISTS checks, so re-running is safe.

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 1, 'Spinach, Egg and Rice Skillet',
       'A fast one-pan dinner that turns basic pantry staples into a filling, protein-rich meal.',
       2, 'Comfort'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 1);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 2, 'Classic Shakshuka',
       'Eggs poached in a spiced tomato and pepper sauce - a one-pan breakfast or dinner.',
       2, 'Middle Eastern'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 2);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 3, 'Salted Caramel Sauce',
       'A rich pourable caramel sauce for drizzling over desserts, coffee, or fruit.',
       6, 'Dessert'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 3);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 4, 'Vegetable Fried Rice',
       'Day-old rice stir-fried with whatever vegetables and aromatics are on hand.',
       3, 'Chinese-inspired'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 4);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 5, 'Spinach and Rice Soup',
       'A light, brothy soup built around rice and greens - easy to digest and low effort.',
       4, 'Comfort'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 5);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 6, 'High-Protein Chickpea Bowl',
       'A vegetarian, protein-forward bowl built around chickpeas, grains, and greens.',
       2, 'Mediterranean'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 6);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 7, 'Simple Egg Fried Rice',
       'The fastest possible use of leftover rice and a couple of eggs.',
       1, 'Chinese-inspired'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 7);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 8, 'Ginger Rice Congee',
       'A gentle, warming rice porridge - easy on the stomach and good when you are unwell.',
       2, 'Chinese'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 8);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 9, 'Weeknight Chicken Ramen',
       'A quick, comforting take on ramen using stock, noodles, and simple toppings.',
       2, 'Japanese-inspired'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 9);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 10, 'Lemon Herb Roast Chicken',
       'A classic whole roast chicken with a bright, herby crust.',
       4, 'Classic'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 10);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 11, 'High-Protein Paneer Stir-Fry',
       'A quick vegetarian stir-fry built around paneer, peppers, and a simple soy-garlic sauce.',
       2, 'Indian-Chinese'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 11);

INSERT INTO recipes (id, title, description, servings, cuisine)
SELECT 12, 'Caramel Apple Skillet',
       'Sauteed apples finished in a quick caramel sauce - a fast, warm dessert.',
       4, 'Dessert'
WHERE NOT EXISTS (SELECT 1 FROM recipes WHERE id = 12);

-- Ingredients
INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 1, v FROM (VALUES ('2 eggs'), ('1 cup cooked rice'), ('2 cups fresh spinach'), ('1 tbsp oil'), ('Salt and pepper')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 1);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 2, v FROM (VALUES ('4 eggs'), ('1 can crushed tomatoes'), ('1 bell pepper, diced'), ('1 onion, diced'), ('2 cloves garlic'), ('1 tsp cumin'), ('1 tsp paprika')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 2);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 3, v FROM (VALUES ('1 cup sugar'), ('6 tbsp butter'), ('1/2 cup heavy cream'), ('1 tsp salt')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 3);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 4, v FROM (VALUES ('3 cups cooked rice'), ('2 eggs'), ('1 cup mixed vegetables'), ('2 tbsp soy sauce'), ('1 tbsp oil'), ('2 green onions')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 4);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 5, v FROM (VALUES ('1 cup rice'), ('4 cups vegetable or chicken broth'), ('2 cups spinach'), ('1 clove garlic'), ('Salt and pepper')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 5);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 6, v FROM (VALUES ('1 can chickpeas'), ('1 cup cooked quinoa or rice'), ('2 cups spinach'), ('2 tbsp olive oil'), ('1 lemon'), ('1 tsp cumin')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 6);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 7, v FROM (VALUES ('1 cup cooked rice'), ('2 eggs'), ('1 tbsp oil'), ('1 tbsp soy sauce'), ('1 green onion')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 7);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 8, v FROM (VALUES ('1/2 cup rice'), ('6 cups water or broth'), ('1 tbsp fresh ginger, sliced'), ('Salt to taste'), ('Green onion to garnish')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 8);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 9, v FROM (VALUES ('4 cups chicken stock'), ('2 portions ramen noodles'), ('1 cooked chicken breast, sliced'), ('2 soft-boiled eggs'), ('1 green onion'), ('1 tbsp soy sauce')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 9);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 10, v FROM (VALUES ('1 whole chicken'), ('2 lemons'), ('4 cloves garlic'), ('2 tbsp fresh thyme'), ('3 tbsp olive oil'), ('Salt and pepper')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 10);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 11, v FROM (VALUES ('200g paneer, cubed'), ('1 bell pepper, sliced'), ('1 onion, sliced'), ('2 tbsp soy sauce'), ('1 tbsp garlic, minced'), ('1 tbsp oil')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 11);

INSERT INTO recipe_ingredients (recipe_id, ingredient)
SELECT 12, v FROM (VALUES ('4 apples, sliced'), ('1/2 cup sugar'), ('4 tbsp butter'), ('1/4 cup heavy cream'), ('1 tsp cinnamon')) AS t(v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredients WHERE recipe_id = 12);

-- Steps
INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 1, ord - 1, v FROM (VALUES
  (1, 'Heat oil in a skillet over medium heat and wilt the spinach, 2 minutes.'),
  (2, 'Push spinach aside, add rice, and warm it through.'),
  (3, 'Make two wells, crack in the eggs, and cook until set to your liking.'),
  (4, 'Season with salt and pepper and serve straight from the skillet.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 1);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 2, ord - 1, v FROM (VALUES
  (1, 'Saute onion and pepper in oil until soft, about 5 minutes.'),
  (2, 'Add garlic, cumin, and paprika; cook 1 minute until fragrant.'),
  (3, 'Pour in crushed tomatoes and simmer 10 minutes until thickened.'),
  (4, 'Make wells in the sauce, crack in the eggs, cover, and cook until whites set.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 2);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 3, ord - 1, v FROM (VALUES
  (1, 'Melt sugar in a dry saucepan over medium heat, swirling until deep amber.'),
  (2, 'Whisk in butter until fully melted.'),
  (3, 'Slowly stream in cream, whisking constantly - it will bubble.'),
  (4, 'Stir in salt and let cool slightly before serving.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 3);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 4, ord - 1, v FROM (VALUES
  (1, 'Heat oil in a wok or large skillet over high heat.'),
  (2, 'Push rice to one side; scramble the eggs on the other, then mix together.'),
  (3, 'Add vegetables and soy sauce, tossing until heated through.'),
  (4, 'Garnish with green onions and serve hot.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 4);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 5, ord - 1, v FROM (VALUES
  (1, 'Bring broth to a simmer with garlic.'),
  (2, 'Add rice and cook until tender, about 15 minutes.'),
  (3, 'Stir in spinach and cook until just wilted.'),
  (4, 'Season with salt and pepper and serve warm.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 5);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 6, ord - 1, v FROM (VALUES
  (1, 'Warm the chickpeas in a pan with a little olive oil and cumin.'),
  (2, 'Add spinach and cook until wilted.'),
  (3, 'Spoon over cooked quinoa or rice.'),
  (4, 'Finish with a squeeze of lemon and a drizzle of olive oil.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 6);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 7, ord - 1, v FROM (VALUES
  (1, 'Heat oil in a pan and scramble the eggs; set aside.'),
  (2, 'Add rice to the same pan and stir-fry 2-3 minutes.'),
  (3, 'Return eggs to the pan, add soy sauce, and toss together.'),
  (4, 'Top with sliced green onion.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 7);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 8, ord - 1, v FROM (VALUES
  (1, 'Combine rice, water, and ginger in a pot.'),
  (2, 'Simmer uncovered, stirring occasionally, 45-60 minutes until porridge-like.'),
  (3, 'Season with salt to taste.'),
  (4, 'Serve topped with sliced green onion.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 8);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 9, ord - 1, v FROM (VALUES
  (1, 'Bring the stock to a simmer and season with soy sauce.'),
  (2, 'Cook the noodles separately according to package instructions.'),
  (3, 'Divide noodles between bowls and ladle over hot stock.'),
  (4, 'Top with sliced chicken, soft-boiled egg, and green onion.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 9);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 10, ord - 1, v FROM (VALUES
  (1, 'Preheat oven to 220C (425F).'),
  (2, 'Rub the chicken all over with oil, garlic, thyme, salt, and pepper.'),
  (3, 'Stuff the cavity with halved lemons.'),
  (4, 'Roast 60-75 minutes until juices run clear, resting 10 minutes before carving.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 10);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 11, ord - 1, v FROM (VALUES
  (1, 'Pan-fry paneer in oil until golden on all sides; set aside.'),
  (2, 'Stir-fry onion and pepper in the same pan until just tender.'),
  (3, 'Add garlic and soy sauce, then return the paneer to the pan.'),
  (4, 'Toss everything together and serve hot.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 11);

INSERT INTO recipe_steps (recipe_id, step_order, step)
SELECT 12, ord - 1, v FROM (VALUES
  (1, 'Melt butter in a skillet and add apples, cooking until just softening.'),
  (2, 'Sprinkle in sugar and cinnamon, stirring until it turns into a light caramel.'),
  (3, 'Stir in cream and simmer 2 minutes until glossy.'),
  (4, 'Serve warm, on its own or over ice cream.')
) AS t(ord, v)
WHERE NOT EXISTS (SELECT 1 FROM recipe_steps WHERE recipe_id = 12);
