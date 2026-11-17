package com.myrtle.middleware.dtos.request;

public record SurveyRequest(
        String question,
        String answer,
        String optionSelected
) {
}