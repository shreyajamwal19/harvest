package com.harvest.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Deleting an account is irreversible - always requires proof of the current password, same
 *  as changing one, never a bare confirmation click alone. */
@Getter
@Setter
public class DeleteAccountRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;
}
