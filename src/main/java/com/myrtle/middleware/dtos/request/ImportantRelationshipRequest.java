package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.AdvisorType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ImportantRelationshipRequest(
        @NotNull(message = "Advisor type is required")
        AdvisorType advisorType,

        @NotBlank(message = "Advisor name or firm is required")
        @Size(max = 200, message = "Advisor name or firm must be at most 200 characters")
        String nameOrFirm
) {
}
