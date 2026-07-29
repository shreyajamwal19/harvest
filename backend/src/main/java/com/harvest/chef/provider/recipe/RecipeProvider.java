package com.harvest.chef.provider.recipe;

import com.harvest.chef.dto.RecipeCandidate;

import java.util.List;

/** A source of recipe candidates. Local and external providers are not interchangeable - each implements this independently. */
public interface RecipeProvider {
    List<RecipeCandidate> search(String query);

    /** Identifies this provider in logs and candidate sourcing, e.g. "local", "themealdb". */
    String providerName();
}
