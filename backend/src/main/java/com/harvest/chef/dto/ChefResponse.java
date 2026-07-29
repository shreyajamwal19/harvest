package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Internal-only output of Composition, before Response Rendering shapes it for the API. */
@Getter
@Builder
public class ChefResponse {
    private ChefResponseType type;
    private String message;
    /** Populated only when type == RECIPE. Empty/null otherwise. */
    private List<RecipeResponse> recipes;
}
