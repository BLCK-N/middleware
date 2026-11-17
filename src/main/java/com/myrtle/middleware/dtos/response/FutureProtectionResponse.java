package com.myrtle.middleware.dtos.response;

public record FutureProtectionResponse(
        Boolean hasWill,
        Boolean hasTrustStructure,
        Boolean hasBusinessSuccessionPlan,
        Boolean hasInsuranceProtection,
        Boolean hasOrganizedFinancialRecords,
        Boolean hasFamilyGovernanceConversations
) {
}
