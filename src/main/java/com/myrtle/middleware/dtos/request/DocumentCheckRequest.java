package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.DocumentOrganizationStatus;
import jakarta.validation.constraints.Size;

public record DocumentCheckRequest(
        DocumentOrganizationStatus documentOrganizationStatus,
        Boolean interestedInOnlineRepository,

        @Size(max = 5000, message = "Document check notes must be at most 5000 characters")
        String notes
) {
}
