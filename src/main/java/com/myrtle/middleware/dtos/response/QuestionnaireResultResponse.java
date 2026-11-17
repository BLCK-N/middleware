package com.myrtle.middleware.dtos.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Client-facing view of a questionnaire result. Deliberately excludes
 * compliance status, flags and the internal report - those are backoffice-only.
 */
public record QuestionnaireResultResponse(
        String id,
        int version,
        String configVersion,
        String persona,
        String personaSecondary,
        boolean hybridPersona,
        String riskProfile,
        BigDecimal riskScore,
        String liquidityNeed,
        String financialCapacity,
        String decisionStyle,
        String communicationPreference,
        List<String> mindsetThemes,
        String clientReportHtml,
        LocalDateTime createdAt
) {
}
