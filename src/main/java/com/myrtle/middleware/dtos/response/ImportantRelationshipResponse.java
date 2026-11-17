package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.AdvisorType;

public record ImportantRelationshipResponse(
        AdvisorType advisorType,
        String nameOrFirm
) {
}
