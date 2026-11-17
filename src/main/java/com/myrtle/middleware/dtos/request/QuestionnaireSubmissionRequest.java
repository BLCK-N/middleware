package com.myrtle.middleware.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record QuestionnaireSubmissionRequest(
        @NotEmpty @Valid List<QuestionAnswer> answers
) {
}
