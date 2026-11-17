package com.myrtle.middleware.dtos.request;

import java.util.List;

public record PersonaQuestionnaireSubmitRequest(
        String userId,
        List<SurveyRequest> responses
) {
}
