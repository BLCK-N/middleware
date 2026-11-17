package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.dtos.request.QuestionAnswer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable, normalized view of a validated submission. Every selection is a
 * "questionCode.optionCode" key ("Q5.A"); free texts are keyed by question code.
 * This is the single input shape every classifier consumes.
 */
public final class ResponseSet {

    private final Set<String> selections;
    private final Map<String, List<String>> optionsByQuestion;
    private final Map<String, String> freeTexts;
    private final Map<String, String> primaryOptions;

    private ResponseSet(Set<String> selections,
                        Map<String, List<String>> optionsByQuestion,
                        Map<String, String> freeTexts,
                        Map<String, String> primaryOptions) {
        this.selections = Collections.unmodifiableSet(selections);
        this.optionsByQuestion = Collections.unmodifiableMap(optionsByQuestion);
        this.freeTexts = Collections.unmodifiableMap(freeTexts);
        this.primaryOptions = Collections.unmodifiableMap(primaryOptions);
    }

    public static ResponseSet from(List<QuestionAnswer> answers) {
        Set<String> selections = new LinkedHashSet<>();
        Map<String, List<String>> optionsByQuestion = new LinkedHashMap<>();
        Map<String, String> freeTexts = new LinkedHashMap<>();
        Map<String, String> primaryOptions = new LinkedHashMap<>();

        for (QuestionAnswer answer : answers) {
            String question = normalize(answer.questionCode());
            List<String> options = answer.selectedOptions() == null ? List.of()
                    : answer.selectedOptions().stream().map(ResponseSet::normalize).toList();

            optionsByQuestion.put(question, options);
            options.forEach(option -> selections.add(question + "." + option));

            if (answer.freeText() != null && !answer.freeText().isBlank()) {
                freeTexts.put(question, answer.freeText().trim());
            }
            if (answer.primaryOption() != null && !answer.primaryOption().isBlank()) {
                primaryOptions.put(question, normalize(answer.primaryOption()));
            }
        }
        return new ResponseSet(selections, optionsByQuestion, freeTexts, primaryOptions);
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase();
    }

    /** @param key selection key, e.g. "Q5.A" */
    public boolean has(String key) {
        return selections.contains(key);
    }

    public boolean hasAnyOf(List<String> keys) {
        return keys.stream().anyMatch(this::has);
    }

    public Set<String> selections() {
        return selections;
    }

    /** The single selected option code of a single-select question, if answered. */
    public Optional<String> singleOption(String questionCode) {
        List<String> options = optionsByQuestion.getOrDefault(questionCode, List.of());
        return options.size() == 1 ? Optional.of(options.get(0)) : Optional.empty();
    }

    public List<String> optionsOf(String questionCode) {
        return optionsByQuestion.getOrDefault(questionCode, List.of());
    }

    public boolean answered(String questionCode) {
        return optionsByQuestion.containsKey(questionCode) || freeTexts.containsKey(questionCode);
    }

    public Optional<String> freeText(String questionCode) {
        return Optional.ofNullable(freeTexts.get(questionCode));
    }

    public Optional<String> primaryOption(String questionCode) {
        return Optional.ofNullable(primaryOptions.get(questionCode));
    }
}
