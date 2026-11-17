package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BiometricRegisterRequest(
        @NotBlank(message = "Device ID is required")
        String deviceId,

        @NotBlank(message = "Public key is required")
        @Size(max = 4096, message = "Public key must be at most 4096 characters")
        String publicKey,

        String algorithm,

        @NotBlank(message = "OTP is required")
        String otp,

        @NotBlank(message = "otpReference is required")
        String otpReference
) {
}
