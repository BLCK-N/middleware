package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * One answered question, referenced by stable codes from the questionnaire
 * scoring config: questionCode "Q1".."Q22", option codes "A".."H".
 *
 * @param questionCode    stable question code, e.g. "Q5"
 * @param selectedOptions selected option codes; single-selects carry exactly one; empty for open text
 * @param primaryOption   the "most important" option for Q1; must be one of selectedOptions
 * @param freeText        open response (Q20) or required explanation for "Others"/UBO answers
 */
public record QuestionAnswer(
        @NotBlank String questionCode,
        List<String> selectedOptions,
        String primaryOption,
        String freeText
) {
}
