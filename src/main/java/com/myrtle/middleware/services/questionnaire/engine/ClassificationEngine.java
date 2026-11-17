package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;

/**
 * Deterministic, rule-based classification of a validated questionnaire
 * submission across the five dimensions defined by the business spec.
 * Pure function of (responses, config) - no Spring, no persistence, no clock -
 * so every classification is reproducible and auditable.
 */
public class ClassificationEngine {

    private final PersonaClassifier personaClassifier = new PersonaClassifier();
    private final RiskClassifier riskClassifier = new RiskClassifier();
    private final LiquidityClassifier liquidityClassifier = new LiquidityClassifier();
    private final CapacityClassifier capacityClassifier = new CapacityClassifier();
    private final DecisionStyleClassifier decisionStyleClassifier = new DecisionStyleClassifier();
    private final ComplianceClassifier complianceClassifier = new ComplianceClassifier();

    public ClassificationResult classify(ResponseSet responses, QuestionnaireScoringConfig config) {
        ClassificationResult.PersonaResult persona = personaClassifier.classify(responses, config);
        ClassificationResult.RiskResult risk = riskClassifier.classify(responses, config);
        ClassificationResult.LiquidityResult liquidity = liquidityClassifier.classify(responses, config);
        ClassificationResult.CapacityResult capacity = capacityClassifier.classify(responses, config);
        ClassificationResult.DecisionStyleResult decisionStyle = decisionStyleClassifier.classify(responses, config);
        ClassificationResult.ComplianceResult compliance =
                complianceClassifier.classify(responses, config, risk, liquidity, capacity);

        return new ClassificationResult(persona, risk, liquidity, capacity, decisionStyle, compliance);
    }
}
