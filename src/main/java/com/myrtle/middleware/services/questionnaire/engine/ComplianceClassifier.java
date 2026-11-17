package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.EnhancedTriggers;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.HoldTriggers;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.OptionDef;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.QuestionDef;
import com.myrtle.middleware.enums.ComplianceStatus;
import com.myrtle.middleware.enums.LiquidityNeed;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.CapacityResult;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.ComplianceResult;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.Flag;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.LiquidityResult;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.RiskResult;

import java.util.ArrayList;
import java.util.List;

class ComplianceClassifier {

    ComplianceResult classify(ResponseSet responses, QuestionnaireScoringConfig config,
                              RiskResult risk, LiquidityResult liquidity, CapacityResult capacity) {
        List<Flag> flags = new ArrayList<>();
        List<String> missingInformation = new ArrayList<>();

        boolean hold = evaluateHoldTriggers(responses, config, flags, missingInformation);
        evaluateEnhancedTriggers(responses, config, risk, liquidity, capacity, flags);

        ComplianceStatus status;
        if (hold) {
            status = ComplianceStatus.HOLD_BEFORE_EXECUTION;
        } else if (!flags.isEmpty()) {
            status = ComplianceStatus.ENHANCED_REVIEW;
        } else {
            status = ComplianceStatus.STANDARD_REVIEW;
        }
        return new ComplianceResult(status, flags, missingInformation);
    }

    private boolean evaluateHoldTriggers(ResponseSet responses, QuestionnaireScoringConfig config,
                                         List<Flag> flags, List<String> missingInformation) {
        HoldTriggers holdTriggers = config.compliance().holdTriggers();
        boolean hold = false;

        String uboNoKey = holdTriggers.uboNoOption();
        int dot = uboNoKey.indexOf('.');
        String uboQuestion = uboNoKey.substring(0, dot);
        if (responses.has(uboNoKey) && responses.freeText(uboQuestion).isEmpty()) {
            flags.add(new Flag("UBO_NO_WITHOUT_DETAILS",
                    "Client is not the ultimate beneficial owner and no representative/authority details were provided"));
            missingInformation.add("Representative / authority details for the beneficial owner");
            hold = true;
        }

        if (holdTriggers.othersUnspecified()) {
            for (QuestionDef question : config.questions()) {
                for (OptionDef option : question.options()) {
                    if (option.requiresText()
                            && responses.has(question.code() + "." + option.code())
                            && responses.freeText(question.code()).isEmpty()) {
                        flags.add(new Flag("REQUIRED_DETAIL_MISSING",
                                question.code() + ": '" + option.label() + "' selected but not specified"));
                        missingInformation.add(question.code() + ": explanation for '" + option.label() + "'");
                        hold = true;
                    }
                }
            }
        }

        if (holdTriggers.missingMandatory()) {
            for (QuestionDef question : config.questions()) {
                if (question.mandatory() && !responses.answered(question.code())) {
                    flags.add(new Flag("MANDATORY_ANSWER_MISSING",
                            question.code() + ": mandatory question not answered"));
                    missingInformation.add(question.code() + ": " + question.label());
                    hold = true;
                }
            }
        }
        return hold;
    }

    private void evaluateEnhancedTriggers(ResponseSet responses, QuestionnaireScoringConfig config,
                                          RiskResult risk, LiquidityResult liquidity, CapacityResult capacity,
                                          List<Flag> flags) {
        EnhancedTriggers enhanced = config.compliance().enhancedTriggers();

        for (String key : enhanced.sourceOfFundsOptions()) {
            if (responses.has(key)) {
                int dot = key.indexOf('.');
                String label = config.optionLabel(key.substring(0, dot), key.substring(dot + 1));
                flags.add(new Flag("SOURCE_OF_FUNDS_REVIEW",
                        "Source of funds includes '" + label + "' - supporting documentation required"));
            }
        }

        for (String key : enhanced.uboOptions()) {
            if (responses.has(key)) {
                flags.add(new Flag("NOT_ULTIMATE_BENEFICIAL_OWNER",
                        "Client is not the ultimate beneficial owner of the funds - identity and authority of the principal must be established"));
            }
        }

        if (responses.hasAnyOf(enhanced.highDebtOptions())) {
            flags.add(new Flag("HIGH_DEBT_OBLIGATIONS",
                    "Client declared substantial outstanding debt obligations"));
        }

        boolean highValue = enhanced.highValueCategories().contains(capacity.category());
        if (highValue && responses.optionsOf("Q21").size() >= enhanced.highValueComplexSofMinSources()) {
            flags.add(new Flag("HIGH_VALUE_COMPLEX_SOURCE_OF_FUNDS",
                    "High-value client with multiple sources of funds - enhanced source-of-funds review recommended"));
        }

        if (enhanced.flagSuitabilityMismatch() && risk.suitabilityMismatch()) {
            flags.add(new Flag("SUITABILITY_MISMATCH",
                    "Client selected long-term aggressive growth but also indicated zero loss tolerance or very high liquidity need"));
        }

        if (enhanced.flagHighLiquidityAggressiveGoal()
                && liquidity.need() == LiquidityNeed.HIGH
                && responses.hasAnyOf(config.riskScoring().suitabilityMismatch().goalTriggers())) {
            flags.add(new Flag("HIGH_LIQUIDITY_AGGRESSIVE_GOAL",
                    "Very high liquidity need combined with a long-term aggressive growth goal"));
        }
    }
}
