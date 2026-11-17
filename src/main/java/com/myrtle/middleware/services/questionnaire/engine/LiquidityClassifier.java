package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.LiquidityFlagRule;
import com.myrtle.middleware.enums.LiquidityNeed;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.Flag;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.LiquidityResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class LiquidityClassifier {

    LiquidityResult classify(ResponseSet responses, QuestionnaireScoringConfig config) {
        LiquidityNeed need = config.liquidity().mapping().entrySet().stream()
                .filter(entry -> responses.has(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(LiquidityNeed.MODERATE);

        List<Flag> flags = new ArrayList<>();
        for (LiquidityFlagRule rule : config.liquidity().flags()) {
            boolean liquidityMatches = rule.whenLiquidity().isEmpty() || rule.whenLiquidity().contains(need);
            if (liquidityMatches && responses.hasAnyOf(rule.andAnyOf())) {
                flags.add(new Flag(rule.code(), rule.description()));
            }
        }
        return new LiquidityResult(need, flags);
    }
}
