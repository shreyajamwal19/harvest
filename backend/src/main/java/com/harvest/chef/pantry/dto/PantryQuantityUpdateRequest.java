package com.harvest.chef.pantry.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PantryQuantityUpdateRequest {

    /** The item is removed outright when this is 0 or less - matches "I ran out of X" semantics. */
    @NotNull(message = "Quantity is required")
    private Double quantity;
}
