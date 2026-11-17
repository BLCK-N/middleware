package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.DecisionStyleResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

class DecisionStyleClassifier {

    private static final String DECISION_QUESTION = "Q17";
    private static final String COMMUNICATION_QUESTION = "Q18";
    private static final String PRIVACY_QUESTION = "Q19";

    DecisionStyleResult classify(ResponseSet responses, QuestionnaireScoringConfig config) {
        Set<String> themes = new LinkedHashSet<>();
        for (Map.Entry<String, String> entry : config.mindsetThemes().entrySet()) {
            if (responses.has(entry.getKey())) {
                themes.add(entry.getValue());
            }
        }

        return new DecisionStyleResult(
                styleFor(responses, config, DECISION_QUESTION),
                styleFor(responses, config, COMMUNICATION_QUESTION),
                styleFor(responses, config, PRIVACY_QUESTION),
                List.copyOf(themes)
        );
    }

    private String styleFor(ResponseSet responses, QuestionnaireScoringConfig config, String question) {
        Map<String, String> mapping = config.decisionStyle().getOrDefault(question, Map.of());
        return responses.singleOption(question).map(mapping::get).orElse(null);
    }
}
