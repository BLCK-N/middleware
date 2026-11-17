package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.ActivationStatus;
import com.myrtle.middleware.model.BiometricCredential;

import java.time.Instant;

public record BiometricCredentialResponse(
        String deviceId,
        String keyId,
        String publicKey,
        String algorithm,
        ActivationStatus status,
        Instant registeredAt
) {
    public static BiometricCredentialResponse from(BiometricCredential credential) {
        return new BiometricCredentialResponse(
                credential.getDevice() != null ? credential.getDevice().getId() : null,
                credential.getKeyId(),
                credential.getPublicKey(),
                credential.getAlgorithm(),
                credential.getStatus(),
                credential.getRegisteredAt()
        );
    }
}
