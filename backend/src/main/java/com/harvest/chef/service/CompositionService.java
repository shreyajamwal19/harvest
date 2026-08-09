package com.harvest.chef.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.ConversationContext;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.dto.RetrievalPlan;
import com.harvest.chef.nutrition.service.NutritionQuestionComposer;
import com.harvest.chef.nutrition.service.NutritionQuestionDetector;
import com.harvest.chef.nutrition.service.NutritionQuestionDetector.NutritionQuestionType;
import com.harvest.chef.pantry.dto.PantrySnapshot;
import com.harvest.chef.pantry.service.PantryCommandDetector;
import com.harvest.chef.pantry.service.PantryCommandDetector.PantryCommand;
import com.harvest.chef.pantry.service.PantryCommandService;
import com.harvest.chef.personalization.service.MemoryCommandDetector;
import com.harvest.chef.personalization.service.MemoryCommandDetector.MemoryCommand;
import com.harvest.chef.personalization.service.MemoryCommandService;
import com.harvest.chef.personalization.service.PreferenceLearningService;
import com.harvest.chef.personalization.service.PreferenceLearningService.LearnedPreference;
import com.harvest.chef.personalization.service.UserProfileService;
import com.harvest.chef.planning.dto.MealPlanResponse;
import com.harvest.chef.planning.dto.ShoppingListResponse;
import com.harvest.chef.planning.service.MealPlanRequestDetector;
import com.harvest.chef.planning.service.MealPlanRequestDetector.MealPlanRequest;
import com.harvest.chef.planning.service.MealPlanningService;
import com.harvest.chef.planning.service.ShoppingListRequestDetector;
import com.harvest.chef.planning.service.ShoppingListService;
import com.harvest.chef.reasoning.ChefReasoningResult;
import com.harvest.chef.reasoning.ChefReasoningService;
import com.harvest.chef.reasoning.ReasoningMode;
import com.harvest.chef.retrieval.FollowUpIntentDetector;
import com.harvest.chef.retrieval.RetrievalPlanningService;
import com.harvest.chef.service.composer.RecipeComposer;
import com.harvest.chef.service.composer.TechniqueAnswerComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Stage 3 - Composition.
 *
 * Checks for a follow-up turn about a recipe already shown this session first
 * ({@link FollowUpIntentDetector#classify}) - if one is classified (comparison, adaptation,
 * coaching, or an "explain why") and the session has previously shown recipes to ground
 * against, it's handled directly via the AI Chef Reasoning Layer with NO retrieval at all
 * (nothing to search or rank; the recipe(s) are already known). A message the detector
 * recognizes as wanting genuinely different recipes ("show another", "I don't like that one")
 * is deliberately left unclassified here, so it falls through to the normal flow below, where
 * {@link RetrievalPlanningService}'s own continuation handling takes it from there.
 *
 * Otherwise, runs the Retrieval Orchestrator's planning step unconditionally right after
 * Context Assembly - there is no Goal Reasoning / Sufficiency Gate stage upstream anymore -
 * then dispatches to the matching composer based on the plan's classified intent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompositionService {

    private final FollowUpIntentDetector followUpIntentDetector;
    private final ChefReasoningService chefReasoningService;
    private final RetrievalPlanningService retrievalPlanningService;
    private final RecipeComposer recipeComposer;
    private final TechniqueAnswerComposer techniqueAnswerComposer;
    private final MemoryCommandDetector memoryCommandDetector;
    private final MemoryCommandService memoryCommandService;
    private final PreferenceLearningService preferenceLearningService;
    private final UserProfileService userProfileService;
    private final PantryCommandDetector pantryCommandDetector;
    private final PantryCommandService pantryCommandService;
    private final MealPlanRequestDetector mealPlanRequestDetector;
    private final MealPlanningService mealPlanningService;
    private final ShoppingListRequestDetector shoppingListRequestDetector;
    private final ShoppingListService shoppingListService;
    private final NutritionQuestionDetector nutritionQuestionDetector;
    private final NutritionQuestionComposer nutritionQuestionComposer;

    public ChefResponse compose(ConversationContext context) {
        // Phase 6A - deterministic memory commands ("remember I like...", "show my
        // preferences", "reset my profile", ...) take absolute priority: no LLM, no
        // retrieval, no risk of a profile question accidentally becoming a recipe search.
        Optional<MemoryCommand> command = memoryCommandDetector.detect(context.getCurrentMessage());
        if (command.isPresent()) {
            return memoryCommandService.execute(context.getUserId(), command.get());
        }

        // Phase 6B - deterministic pantry commands ("I bought eggs", "remove onions", "show my
        // pantry", ...), same priority reasoning: never let a pantry update accidentally become
        // a recipe search.
        Optional<PantryCommand> pantryCommand = pantryCommandDetector.detect(context.getCurrentMessage());
        if (pantryCommand.isPresent()) {
            return pantryCommandService.execute(context.getUserId(), pantryCommand.get());
        }

        // Phase 6B - meal-plan and shopping-list requests are also deterministic (the engine
        // chooses every recipe / every list item, never the LLM) and checked before normal
        // retrieval so they can't be misrouted into an ordinary recipe search.
        Optional<MealPlanRequest> mealPlanRequest = mealPlanRequestDetector.detect(context.getCurrentMessage());
        if (mealPlanRequest.isPresent()) {
            return composeMealPlan(context, mealPlanRequest.get());
        }
        if (shoppingListRequestDetector.detect(context.getCurrentMessage())) {
            return composeShoppingList(context);
        }

        Optional<ChefResponse> followUp = tryComposeFollowUp(context);
        if (followUp.isPresent()) {
            return followUp.get();
        }

        RetrievalPlan plan = retrievalPlanningService.plan(context);

        ChefResponse response = switch (plan.getIntent()) {
            case TECHNIQUE -> techniqueAnswerComposer.compose(context, plan);
            case RECIPE -> recipeComposer.compose(context, plan);
        };

        return acknowledgeAnyLearnedPreferences(context, response);
    }

    /** MEAL_PLANNING - the deterministic engine chooses every day's recipe; see MealPlanningService. */
    private ChefResponse composeMealPlan(ConversationContext context, MealPlanRequest request) {
        MealPlanResponse plan = mealPlanningService.generate(context, request.days(), request.mealType());

        if (plan.getDays().isEmpty()) {
            return ChefResponse.builder()
                    .type(ChefResponseType.MEAL_PLAN)
                    .message("I couldn't put together a meal plan from what's available right now.")
                    .mealPlan(plan)
                    .build();
        }

        String summary = plan.getDays().stream()
                .map(day -> day.getDayLabel() + ": " + day.getRecipe().getTitle())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String message = "Here's a " + plan.getDays().size() + "-day plan:\n" + summary;

        return ChefResponse.builder()
                .type(ChefResponseType.MEAL_PLAN)
                .message(message)
                .mealPlan(plan)
                .build();
    }

    /** SHOPPING_LISTS - built from whatever recipes were most recently shown (search or meal plan). */
    private ChefResponse composeShoppingList(ConversationContext context) {
        List<RecipeResponse> source = context.getLastShownRecipes();
        if (source == null || source.isEmpty()) {
            return ChefResponse.builder()
                    .type(ChefResponseType.SHOPPING_LIST)
                    .message("I don't have any recipes to shop for yet - ask me for a recipe or a "
                            + "meal plan first, then I can generate a grocery list from it.")
                    .build();
        }

        PantrySnapshot pantry = context.getPantry();
        ShoppingListResponse list = shoppingListService.generate(source, pantry);

        if (list.getCategories().isEmpty()) {
            return ChefResponse.builder()
                    .type(ChefResponseType.SHOPPING_LIST)
                    .message("Looks like your pantry already covers everything for what's been suggested "
                            + "- nothing to buy!")
                    .shoppingList(list)
                    .build();
        }

        String summary = list.getCategories().stream()
                .map(c -> c.getCategory() + ": " + String.join(", ", c.getItems()))
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        return ChefResponse.builder()
                .type(ChefResponseType.SHOPPING_LIST)
                .message("Here's your grocery list:\n" + summary)
                .shoppingList(list)
                .build();
    }

    /**
     * Passive/explicit preference learning from ordinary conversation (not a memory command) -
     * "I love spicy food", "I'm vegetarian", etc. Runs after composition so the normal
     * recipe/technique response is never delayed or altered by it; if anything was learned, a
     * short natural acknowledgment is appended to the message that's already been composed.
     */
    private ChefResponse acknowledgeAnyLearnedPreferences(ConversationContext context, ChefResponse response) {
        List<LearnedPreference> learned = preferenceLearningService
                .learnFromMessage(userProfileService, context.getUserId(), context.getCurrentMessage());
        if (learned.isEmpty()) {
            return response;
        }

        String acknowledgment = learned.stream()
                .map(lp -> lp.positive() ? "you like " + lp.value() : "you don't like " + lp.value())
                .reduce((a, b) -> a + " and " + b)
                .map(s -> "Noted that " + s + ".")
                .orElse("");

        return ChefResponse.builder()
                .type(response.getType())
                .message(response.getMessage() + " " + acknowledgment)
                .recipes(response.getRecipes())
                .build();
    }

    /**
     * @return a composed response if this turn is a follow-up about previously shown recipe(s)
     *         AND the AI Chef Reasoning Layer is available to reason about it; empty otherwise,
     *         in which case the caller falls through to the normal retrieval/planning flow.
     */
    private Optional<ChefResponse> tryComposeFollowUp(ConversationContext context) {
        List<RecipeResponse> previouslyShown = context.getLastShownRecipes();
        if (previouslyShown == null || previouslyShown.isEmpty()) {
            return Optional.empty();
        }

        // Phase 7 (Part 2) - a nutrition question about the shown recipe is answered with real
        // USDA data, not the LLM's own guess. Checked before the general follow-up classifier
        // for the same reason Phase 6A/6B's deterministic commands are checked before
        // retrieval: a more specific, groundable question always wins over a generic catch-all.
        Optional<NutritionQuestionType> nutritionQuestion =
                nutritionQuestionDetector.detect(context.getCurrentMessage());
        if (nutritionQuestion.isPresent()) {
            return Optional.of(nutritionQuestionComposer.compose(nutritionQuestion.get(), previouslyShown.get(0)));
        }

        Optional<ReasoningMode> mode = followUpIntentDetector.classify(context.getCurrentMessage());
        if (mode.isEmpty()) {
            return Optional.empty();
        }

        Optional<ChefReasoningResult> reasoning =
                chefReasoningService.reasonAboutFollowUp(context, mode.get(), previouslyShown);
        if (reasoning.isEmpty()) {
            // The reasoning layer is what makes a follow-up turn meaningful (adapting/comparing/
            // coaching on the shown recipe(s) in prose) - without it, there's nothing useful to
            // add beyond re-running retrieval, so fall through to the normal flow rather than guess.
            log.info("[ai-chef] Follow-up classified as mode={} but reasoning layer unavailable - "
                    + "falling back to normal retrieval flow.", mode.get());
            return Optional.empty();
        }

        log.info("[ai-chef] Follow-up turn handled via AI Chef Reasoning Layer (mode={}), no retrieval run.",
                mode.get());
        return Optional.of(ChefResponse.builder()
                .type(ChefResponseType.RECIPE)
                .message(reasoning.get().getMessage())
                .recipes(previouslyShown)
                .build());
    }
}
