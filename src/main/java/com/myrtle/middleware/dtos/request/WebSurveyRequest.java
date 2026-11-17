package com.myrtle.middleware.dtos.request;

import java.util.List;

public record WebSurveyRequest(
        String email,
        String gender,
        String fullName,
        List<SurveyRequest> responses
) {
}
