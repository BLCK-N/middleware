package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BiometricValidateRequest(
        @NotBlank(message = "Public key is required")
        @Size(max = 4096, message = "Public key must be at most 4096 characters")
        String publicKey,

        String deviceId
) {
}
