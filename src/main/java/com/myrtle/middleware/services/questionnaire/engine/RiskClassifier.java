package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.RiskCap;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.RiskScoring;
import com.myrtle.middleware.enums.RiskProfile;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.AppliedCap;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.RiskResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class RiskClassifier {

    RiskResult classify(ResponseSet responses, QuestionnaireScoringConfig config) {
        RiskScoring rules = config.riskScoring();

        Map<String, Integer> perQuestionScores = new LinkedHashMap<>();
        BigDecimal weighted = BigDecimal.ZERO;
        for (Map.Entry<String, Map<String, Integer>> entry : rules.questionScores().entrySet()) {
            String question = entry.getKey();
            Integer score = responses.singleOption(question)
                    .map(option -> entry.getValue().get(option))
                    .orElse(null);
            if (score == null) {
                continue;
            }
            perQuestionScores.put(question, score);
            weighted = weighted.add(BigDecimal.valueOf(score)
                    .multiply(BigDecimal.valueOf(rules.weights().get(question))));
        }
        weighted = weighted.setScale(2, RoundingMode.HALF_UP);

        RiskProfile banded = bandFor(weighted, rules);
        RiskProfile profile = banded;

        List<AppliedCap> appliedCaps = new ArrayList<>();
        for (RiskCap cap : rules.caps()) {
            if (!responses.has(cap.trigger())) {
                continue;
            }
            RiskProfile capped = profile.cappedAt(cap.maxProfile());
            if (capped != profile) {
                appliedCaps.add(new AppliedCap(cap.trigger(), profile, capped, cap.reason(),
                        cap.advisorOverridable()));
                profile = capped;
            }
        }

        boolean mismatch = responses.hasAnyOf(rules.suitabilityMismatch().goalTriggers())
                && responses.hasAnyOf(rules.suitabilityMismatch().conflictTriggers());

        return new RiskResult(perQuestionScores, weighted, banded, profile, appliedCaps, mismatch);
    }

    private RiskProfile bandFor(BigDecimal weightedScore, RiskScoring rules) {
        return rules.bands().stream()
                .filter(band -> weightedScore.doubleValue() <= band.maxInclusive())
                .findFirst()
                .map(QuestionnaireScoringConfig.RiskBand::profile)
                .orElse(rules.bands().get(rules.bands().size() - 1).profile());
    }
}
