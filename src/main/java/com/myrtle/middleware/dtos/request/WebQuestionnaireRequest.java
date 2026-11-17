package com.myrtle.middleware.dtos.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record WebQuestionnaireRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotEmpty @Valid List<QuestionAnswer> answers
) {
}
