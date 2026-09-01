package com.harvest.chef.pantry.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class PantryItemRequest {

    @NotBlank(message = "Ingredient name is required")
    @Size(max = 200, message = "Ingredient name must be 200 characters or fewer")
    private String ingredientName;

    /** Optional - "I have chicken" with no stated amount is a valid pantry entry. */
    @Positive(message = "Quantity must be greater than zero")
    private Double quantity;

    @Size(max = 30, message = "Unit must be 30 characters or fewer")
    private String unit;

    /** Optional. Never inferred if omitted - only ever what the person explicitly set. */
    private LocalDate expiryDate;
}
