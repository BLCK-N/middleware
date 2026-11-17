package com.myrtle.middleware.dtos.response;

import java.time.LocalDateTime;

public record QuestionnaireHistoryItem(
        String id,
        int version,
        boolean latest,
        String persona,
        String riskProfile,
        LocalDateTime createdAt
) {
}
