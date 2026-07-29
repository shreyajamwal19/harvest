package com.harvest.chef.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatResponse {
    private Long sessionId;
    private ChefResponseType responseType;
    private String message;
    private RecipeResponse recipe;
}
