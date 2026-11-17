package com.myrtle.middleware.dtos.response;

public record QuestionnaireSubmitResponse(
        String id,
        int version,
        String persona,
        String riskProfile
) {
}
