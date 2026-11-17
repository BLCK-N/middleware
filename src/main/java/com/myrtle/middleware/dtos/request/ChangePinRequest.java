package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePinRequest(
        @NotBlank(message = "pin must be supplied")
        @Pattern(regexp = "^\\d{4}$", message = "Pin must be exactly 4 digits")
        String pin,

        @NotBlank(message = "otp must have been validated")
        String otpReference
) {
}
