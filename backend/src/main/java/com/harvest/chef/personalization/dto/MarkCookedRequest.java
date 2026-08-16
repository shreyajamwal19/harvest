package com.harvest.chef.personalization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MarkCookedRequest {

    @NotBlank(message = "Recipe title is required")
    private String title;
}
