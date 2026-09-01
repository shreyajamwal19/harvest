package com.harvest.chef.pantry.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** No @NotNull - a null expiryDate is a valid request meaning "stop tracking expiry for this item". */
@Getter
@Setter
public class PantryExpiryUpdateRequest {
    private LocalDate expiryDate;
}
