package com.myrtle.middleware.services.questionnaire;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.OptionDef;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.QuestionDef;
import com.myrtle.middleware.dtos.request.QuestionAnswer;
import com.myrtle.middleware.enums.QuestionType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Config-driven validation of a questionnaire submission against the question
 * catalog. Returns accumulated human-readable errors; empty list means valid.
 */
@Component
public class QuestionnaireValidator {

    public List<String> validate(List<QuestionAnswer> answers, QuestionnaireScoringConfig config) {
        List<String> errors = new ArrayList<>();
        if (answers == null || answers.isEmpty()) {
            errors.add("No answers submitted");
            return errors;
        }

        Map<String, QuestionAnswer> byCode = new HashMap<>();
        for (QuestionAnswer answer : answers) {
            if (answer.questionCode() == null || answer.questionCode().isBlank()) {
                errors.add("An answer is missing its questionCode");
                continue;
            }
            String code = answer.questionCode().trim().toUpperCase();
            if (byCode.put(code, answer) != null) {
                errors.add(code + ": duplicate answer submitted");
            }
            if (config.questionByCode(code).isEmpty()) {
                errors.add(code + ": unknown question code");
            }
        }

        for (QuestionDef question : config.questions()) {
            QuestionAnswer answer = byCode.get(question.code());
            if (answer == null) {
                if (question.mandatory()) {
                    errors.add(question.code() + ": mandatory question not answered");
                }
                continue;
            }
            validateAnswer(errors, question, answer);
        }
        return errors;
    }

    private void validateAnswer(List<String> errors, QuestionDef question, QuestionAnswer answer) {
        String code = question.code();
        List<String> selected = answer.selectedOptions() == null ? List.of()
                : answer.selectedOptions().stream().map(o -> o == null ? "" : o.trim().toUpperCase()).toList();

        if (question.type() == QuestionType.OPEN_TEXT) {
            if (answer.freeText() == null || answer.freeText().isBlank()) {
                errors.add(code + ": a written response is required");
            }
            return;
        }

        if (selected.isEmpty()) {
            errors.add(code + ": at least one option must be selected");
            return;
        }

        Set<String> validOptions = new HashSet<>();
        question.options().forEach(option -> validOptions.add(option.code()));
        for (String option : selected) {
            if (!validOptions.contains(option)) {
                errors.add(code + ": unknown option '" + option + "'");
            }
        }

        if (question.type() == QuestionType.SINGLE_SELECT && selected.size() > 1) {
            errors.add(code + ": only one option may be selected");
        }

        if (question.type() == QuestionType.MULTI_SELECT_WITH_PRIMARY) {
            String primary = answer.primaryOption() == null ? "" : answer.primaryOption().trim().toUpperCase();
            if (primary.isBlank()) {
                errors.add(code + ": the most important option must be indicated");
            } else if (!selected.contains(primary)) {
                errors.add(code + ": the most important option must be one of the selected options");
            }
        }

        for (OptionDef option : question.options()) {
            if (option.requiresText() && selected.contains(option.code())
                    && (answer.freeText() == null || answer.freeText().isBlank())) {
                errors.add(code + ": option '" + option.code() + "' requires an explanation");
            }
        }
    }
}
