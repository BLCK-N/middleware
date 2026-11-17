package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.ActivationStatus;

public record BiometricValidationResponse(
        boolean valid,
        String deviceId,
        String keyId,
        String algorithm,
        ActivationStatus status
) {
}
