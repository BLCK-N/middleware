package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ValidateBvnRequest(
        @NotBlank(message = "BVN is required")
        @Size(min = 11, max = 11, message = "BVN must be 11 characters")
        String bvn
) {
}
