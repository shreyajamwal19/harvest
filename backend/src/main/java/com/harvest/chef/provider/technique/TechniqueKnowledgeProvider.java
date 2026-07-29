package com.harvest.chef.provider.technique;

/** Answers cooking-method/food-science questions that never need recipe retrieval. */
public interface TechniqueKnowledgeProvider {
    String answer(String question, String interpretedGoal);
}
