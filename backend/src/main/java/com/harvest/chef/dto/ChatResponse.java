package com.harvest.chef.dto;

import com.harvest.chef.planning.dto.MealPlanResponse;
import com.harvest.chef.planning.dto.ShoppingListResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatResponse {
    private Long sessionId;
    private ChefResponseType responseType;
    private String message;
    private List<RecipeResponse> recipes;
    /** Phase 6B - populated only when responseType == MEAL_PLAN. Additive, backward compatible. */
    private MealPlanResponse mealPlan;
    /** Phase 6B - populated only when responseType == SHOPPING_LIST. Additive, backward compatible. */
    private ShoppingListResponse shoppingList;
}
