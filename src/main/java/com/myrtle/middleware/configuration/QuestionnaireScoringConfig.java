package com.myrtle.middleware.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.myrtle.middleware.enums.FinancialCapacity;
import com.myrtle.middleware.enums.LiquidityNeed;
import com.myrtle.middleware.enums.QuestionType;
import com.myrtle.middleware.enums.RiskProfile;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable, typed mirror of questionnaire-scoring-config.json.
 * Scoring keys are "questionCode.optionCode", e.g. "Q5.A".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QuestionnaireScoringConfig(
        String configVersion,
        List<QuestionDef> questions,
        Map<String, Map<String, Integer>> personaScoring,
        PersonaRules personaRules,
        RiskScoring riskScoring,
        LiquidityRules liquidity,
        CapacityScoring capacityScoring,
        Map<String, Map<String, String>> decisionStyle,
        Map<String, String> mindsetThemes,
        ComplianceRules compliance
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuestionDef(String code, QuestionType type, boolean mandatory, String label,
                              List<OptionDef> options) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OptionDef(String code, String label, boolean requiresText) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PersonaRules(int hybridThreshold, int primaryOptionMultiplier,
                               NetWorthOverride netWorthOverride) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record NetWorthOverride(List<String> liquidAssetTriggers, List<String> hardAssetTriggers,
                                   List<String> legacyIntentTriggers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskScoring(Map<String, Map<String, Integer>> questionScores,
                              Map<String, Double> weights,
                              Map<String, String> areaNames,
                              List<RiskBand> bands,
                              List<RiskCap> caps,
                              SuitabilityMismatch suitabilityMismatch) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskBand(double maxInclusive, RiskProfile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RiskCap(String trigger, RiskProfile maxProfile, String reason, boolean advisorOverridable) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SuitabilityMismatch(List<String> goalTriggers, List<String> conflictTriggers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LiquidityRules(Map<String, LiquidityNeed> mapping, List<LiquidityFlagRule> flags) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LiquidityFlagRule(String code, String description, List<LiquidityNeed> whenLiquidity,
                                    List<String> andAnyOf) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapacityScoring(Map<String, Map<String, Double>> questionScores, List<CapacityBand> bands) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CapacityBand(double maxInclusive, FinancialCapacity category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ComplianceRules(EnhancedTriggers enhancedTriggers, HoldTriggers holdTriggers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnhancedTriggers(List<String> sourceOfFundsOptions,
                                   List<String> uboOptions,
                                   List<String> highDebtOptions,
                                   List<FinancialCapacity> highValueCategories,
                                   int highValueComplexSofMinSources,
                                   boolean flagSuitabilityMismatch,
                                   boolean flagHighLiquidityAggressiveGoal) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HoldTriggers(String uboNoOption, boolean othersUnspecified, boolean missingMandatory) {
    }

    public Optional<QuestionDef> questionByCode(String code) {
        return questions.stream().filter(q -> q.code().equals(code)).findFirst();
    }

    /**
     * Checks a "Qn.X" scoring key against the question catalog.
     */
    public boolean selectionKeyExists(String key) {
        int dot = key.indexOf('.');
        if (dot <= 0 || dot == key.length() - 1) {
            return false;
        }
        String questionCode = key.substring(0, dot);
        String optionCode = key.substring(dot + 1);
        return questionByCode(questionCode)
                .map(q -> q.options().stream().anyMatch(o -> o.code().equals(optionCode)))
                .orElse(false);
    }

    public String optionLabel(String questionCode, String optionCode) {
        return questionByCode(questionCode)
                .flatMap(q -> q.options().stream().filter(o -> o.code().equals(optionCode)).findFirst())
                .map(OptionDef::label)
                .orElse(optionCode);
    }
}
