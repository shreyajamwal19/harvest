package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

/** Internal-only output of Composition, before Response Rendering shapes it for the API. */
@Getter
@Builder
public class ChefResponse {
    private ChefResponseType type;
    private String message;
    private RecipeResponse recipe;
}
