package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.myrtle.middleware.enums.ProofType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record Tier2UpgradePayload(

        @NotBlank(message = "NIN is required")
        String idNumber,

        /** Optional; defaults to the authenticated user's first name when blank. */
        String firstname,

        /** Optional; defaults to the authenticated user's last name when blank. */
        String lastname,

        @NotEmpty(message = "At least one identity document URL is required")
        String[] proof,

        @NotNull(message = "Proof type is required")
        ProofType proofType
) {
}
