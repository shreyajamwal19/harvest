package com.harvest.chef.nutrition.service;

import com.harvest.chef.dto.ChefResponse;
import com.harvest.chef.dto.ChefResponseType;
import com.harvest.chef.dto.NutritionInfo;
import com.harvest.chef.dto.RecipeResponse;
import com.harvest.chef.knowledge.manager.KnowledgeProviderManager;
import com.harvest.chef.nutrition.service.NutritionQuestionDetector.NutritionQuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Answers a nutrition question about the recipe already shown using real USDA FoodData Central
 * numbers - never the LLM's own guess (Part 2: "Never hallucinate nutrition"). Every answer
 * states plainly how many of the recipe's ingredients USDA data was actually found for, and is
 * explicit that totals are summed USDA per-100g reference values (not scaled to the recipe's
 * actual stated quantities, which the dataset doesn't carry in a structured form) - honest
 * about the limits of what's grounded rather than implying more precision than exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionQuestionComposer {

    private final KnowledgeProviderManager knowledgeProviderManager;
    private final RecipeIngredientNameExtractor ingredientNameExtractor;

    public ChefResponse compose(NutritionQuestionType questionType, RecipeResponse recipe) {
        List<String> foodNames = ingredientNameExtractor.extractAll(recipe.getIngredients());
        List<NutritionInfo> found = knowledgeProviderManager.retrieveNutrition(foodNames);

        log.info("[nutrition] question type={} recipe='{}' ingredientsChecked={} usdaMatches={}",
                questionType, recipe.getTitle(), foodNames.size(), found.size());

        if (found.isEmpty()) {
            return response("I don't have verified USDA nutrition data for \"" + recipe.getTitle()
                    + "\" right now, so I can't give you real numbers rather than guess at them. "
                    + "Nutrition grounding needs a configured USDA API key.");
        }

        String coverage = " (based on USDA data for " + found.size() + " of " + foodNames.size()
                + " ingredients, per USDA's standard 100g reference amounts - not scaled to this "
                + "recipe's actual quantities)";

        return switch (questionType) {
            case CALORIES -> response("Roughly " + roundedSum(found, NutritionInfo::getCalories)
                    + " kcal total across the ingredients USDA has data for" + coverage + ".");
            case PROTEIN -> response("About " + roundedSum(found, NutritionInfo::getProteinGrams)
                    + "g of protein across those ingredients" + coverage + ".");
            case CARBS -> response("About " + roundedSum(found, NutritionInfo::getCarbsGrams)
                    + "g of carbohydrates across those ingredients" + coverage + ".");
            case FAT -> response("About " + roundedSum(found, NutritionInfo::getFatGrams)
                    + "g of fat across those ingredients" + coverage + ".");
            case FIBER -> response("About " + roundedSum(found, NutritionInfo::getFiberGrams)
                    + "g of fiber across those ingredients" + coverage + ".");
            case SODIUM -> response("About " + roundedSum(found, NutritionInfo::getSodiumMg)
                    + "mg of sodium across those ingredients" + coverage + ".");
            case GENERAL_HEALTHY -> generalHealthAnswer(found, coverage);
        };
    }

    private ChefResponse generalHealthAnswer(List<NutritionInfo> found, String coverage) {
        double protein = roundedSum(found, NutritionInfo::getProteinGrams);
        double fiber = roundedSum(found, NutritionInfo::getFiberGrams);
        double sodium = roundedSum(found, NutritionInfo::getSodiumMg);

        StringBuilder message = new StringBuilder("Based on what's grounded in USDA data" + coverage + ": ");
        message.append("roughly ").append(protein).append("g protein and ")
                .append(fiber).append("g fiber");
        if (sodium > 0) {
            message.append(", about ").append(sodium).append("mg sodium");
        }
        message.append(". I'll leave it to you to weigh that against your own goals rather than "
                + "label it \"healthy\" or not for you.");
        return response(message.toString());
    }

    private double roundedSum(List<NutritionInfo> found, java.util.function.Function<NutritionInfo, Double> field) {
        double sum = found.stream().map(field).filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).sum();
        return Math.round(sum * 10.0) / 10.0;
    }

    private ChefResponse response(String message) {
        return ChefResponse.builder()
                .type(ChefResponseType.TECHNIQUE_ANSWER)
                .message(message)
                .recipes(null)
                .build();
    }
}
