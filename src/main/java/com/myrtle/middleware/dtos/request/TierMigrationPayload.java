package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.ProofType;

public record TierMigrationPayload(
        String idNumber,
        String firstname,
        String lastname,
        String address,
        String[] proof,
        ProofType proofType
) {
}
