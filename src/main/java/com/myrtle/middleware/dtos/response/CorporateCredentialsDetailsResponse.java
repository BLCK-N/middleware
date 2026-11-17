package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;
import org.springframework.util.StringUtils;

/**
 * What page 7 (credentials) currently holds. The PIN and password are write-only - they are
 * reported as set/not set so the frontend can show the state of the form without ever reading the
 * values back.
 */
public record CorporateCredentialsDetailsResponse(
        boolean collected,
        boolean pinSet,
        boolean passwordSet,
        String username,
        String profession,
        String nationality,
        String idExpDate,
        String valuationCurrency,
        String businessStructure,
        String title,
        String maritalStatus,
        String employerName,
        String employerAddress,
        String netWorth,
        String annualIncome,
        String liquidNetWorth,
        String sourceOfWealth,
        String investmentExperience,
        String riskTolerance,
        String investmentObjective
) {

    public static CorporateCredentialsDetailsResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }
        boolean pinSet = StringUtils.hasText(pending.getPin());
        boolean passwordSet = StringUtils.hasText(pending.getPassword());

        return new CorporateCredentialsDetailsResponse(
                pinSet && passwordSet,
                pinSet,
                passwordSet,
                pending.getUsername(),
                pending.getProfession(),
                pending.getNationality(),
                pending.getIdExpiry(),
                pending.getValuationCurrency(),
                pending.getBusinessStructure(),
                pending.getTitle(),
                pending.getMaritalStatus(),
                pending.getEmployerName(),
                pending.getEmployerAddress(),
                pending.getNetWorth(),
                pending.getAnnualIncome(),
                pending.getLiquidNetWorth(),
                pending.getSourceOfWealth(),
                pending.getInvestmentExperience(),
                pending.getRiskTolerance(),
                pending.getInvestmentObjective()
        );
    }
}
