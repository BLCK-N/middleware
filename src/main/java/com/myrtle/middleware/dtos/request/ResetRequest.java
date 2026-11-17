package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record ResetRequest(
        String email,
        String password,
        @NotBlank(message = "Otp must have been validated")
        String otpReference
) {
}
