package com.myrtle.middleware.dtos.request;

public record FutureProtectionRequest(
        Boolean hasWill,
        Boolean hasTrustStructure,
        Boolean hasBusinessSuccessionPlan,
        Boolean hasInsuranceProtection,
        Boolean hasOrganizedFinancialRecords,
        Boolean hasFamilyGovernanceConversations
) {
}
