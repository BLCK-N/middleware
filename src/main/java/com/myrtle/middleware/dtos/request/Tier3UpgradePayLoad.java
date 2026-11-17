package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.ProofType;

public record Tier3UpgradePayLoad(
        String address,
        String[] proof,
        ProofType proofType
) {
}
