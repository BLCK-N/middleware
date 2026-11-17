package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for setting or updating PIN for both normal users and corporate accounts.
 * The system will determine the account type based on the authenticated JWT token.
 */
public record SetPinRequest(
        @NotBlank(message = "pin must be supplied")
        @Pattern(regexp = "^\\d{4}$", message = "Pin must be exactly 4 digits")
        String pin
) {
}
