package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.DocumentOrganizationStatus;

public record DocumentCheckResponse(
        DocumentOrganizationStatus documentOrganizationStatus,
        Boolean interestedInOnlineRepository,
        String notes
) {
}
