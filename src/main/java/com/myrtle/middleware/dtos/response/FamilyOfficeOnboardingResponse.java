package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.PriorityArea;

import java.time.LocalDateTime;
import java.util.List;

public record FamilyOfficeOnboardingResponse(
        String id,
        PersonalInformationResponse personalInformation,
        FamilyInformationResponse familyInformation,
        WealthJourneyResponse wealthJourney,
        FinancialWorldResponse financialWorld,
        List<ImportantRelationshipResponse> importantRelationships,
        FutureProtectionResponse futureProtection,
        DocumentCheckResponse documentCheck,
        List<PriorityArea> priorityAreas,
        String finalReflection,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
