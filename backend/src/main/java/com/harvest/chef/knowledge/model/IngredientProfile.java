package com.harvest.chef.knowledge.model;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IngredientProfile {
    private String name;
    private List<String> substitutes;
    private List<String> flavorPairings;
    private String storageAdvice;
    private String shelfLife;
    private String seasonality;
    private List<String> preparationTips;
    private String source;
}
