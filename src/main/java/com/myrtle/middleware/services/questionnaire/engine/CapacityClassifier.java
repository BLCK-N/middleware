package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.enums.FinancialCapacity;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.CapacityResult;

import java.util.LinkedHashMap;
import java.util.Map;

class CapacityClassifier {

    CapacityResult classify(ResponseSet responses, QuestionnaireScoringConfig config) {
        Map<String, Double> perQuestionScores = new LinkedHashMap<>();
        double total = 0;
        for (Map.Entry<String, Map<String, Double>> entry : config.capacityScoring().questionScores().entrySet()) {
            String question = entry.getKey();
            Double score = responses.singleOption(question)
                    .map(option -> entry.getValue().get(option))
                    .orElse(null);
            if (score == null) {
                continue;
            }
            perQuestionScores.put(question, score);
            total += score;
        }

        double finalTotal = total;
        FinancialCapacity category = config.capacityScoring().bands().stream()
                .filter(band -> finalTotal <= band.maxInclusive())
                .findFirst()
                .map(QuestionnaireScoringConfig.CapacityBand::category)
                .orElse(FinancialCapacity.PRIVATE_WEALTH_UHNI);

        return new CapacityResult(perQuestionScores, total, category);
    }
}
