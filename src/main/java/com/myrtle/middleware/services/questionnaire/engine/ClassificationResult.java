package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.enums.ComplianceStatus;
import com.myrtle.middleware.enums.FinancialCapacity;
import com.myrtle.middleware.enums.LiquidityNeed;
import com.myrtle.middleware.enums.RiskProfile;
import com.myrtle.middleware.enums.WealthPersona;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Full deterministic classification output, including the breakdowns that make
 * every result auditable and explainable.
 */
public record ClassificationResult(
        PersonaResult persona,
        RiskResult risk,
        LiquidityResult liquidity,
        CapacityResult capacity,
        DecisionStyleResult decisionStyle,
        ComplianceResult compliance
) {

    public record PersonaResult(
            WealthPersona persona,
            WealthPersona secondaryPersona,
            boolean hybrid,
            boolean netWorthOverrideApplied,
            Map<WealthPersona, Integer> scores,
            List<String> reasons
    ) {
        public String displayLabel() {
            if (netWorthOverrideApplied) {
                return persona.getDisplayName();
            }
            if (hybrid && secondaryPersona != null) {
                return persona.getDisplayName() + " with " + secondaryPersona.getDisplayName() + " Orientation";
            }
            return persona.getDisplayName();
        }
    }

    public record RiskResult(
            Map<String, Integer> perQuestionScores,
            BigDecimal weightedScore,
            RiskProfile preOverrideProfile,
            RiskProfile profile,
            List<AppliedCap> appliedCaps,
            boolean suitabilityMismatch
    ) {
    }

    public record AppliedCap(
            String trigger,
            RiskProfile from,
            RiskProfile to,
            String reason,
            boolean advisorOverridable
    ) {
    }

    public record LiquidityResult(
            LiquidityNeed need,
            List<Flag> flags
    ) {
    }

    public record CapacityResult(
            Map<String, Double> perQuestionScores,
            double totalScore,
            FinancialCapacity category
    ) {
    }

    public record DecisionStyleResult(
            String decisionStyle,
            String communicationPreference,
            String privacyOpenness,
            List<String> mindsetThemes
    ) {
    }

    public record ComplianceResult(
            ComplianceStatus status,
            List<Flag> flags,
            List<String> missingInformation
    ) {
    }

    public record Flag(String code, String description) {
    }
}
