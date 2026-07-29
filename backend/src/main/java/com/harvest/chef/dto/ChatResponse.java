package com.harvest.chef.dto;

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
}
