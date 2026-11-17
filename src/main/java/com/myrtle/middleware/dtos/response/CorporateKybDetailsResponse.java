package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * What page 4 (KYB) currently holds. Only {@code rcNumber} was typed by the user - everything else
 * came back from the CAC lookup, and is returned so the frontend can display the verified company
 * record it matched.
 */
public record CorporateKybDetailsResponse(
        boolean collected,
        boolean cacVerified,
        String rcNumber,
        String companyName,
        String companyType,
        String businessStructure,
        OffsetDateTime registrationDate,
        String classification,
        String companyStatus,
        String companyEmail,
        String headOfficeAddress,
        String branchAddress,
        String addressLine2,
        String state,
        String city,
        String lga,
        String postCode,
        String country,
        BigDecimal shareCapital,
        String shareCapitalInWords,
        Integer numberOfAffiliates
) {

    public static CorporateKybDetailsResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }
        return new CorporateKybDetailsResponse(
                pending.isCacVerified(),
                pending.isCacVerified(),
                pending.getRcNumber(),
                pending.getCompanyName(),
                pending.getCompanyType(),
                pending.getBusinessStructure(),
                pending.getRegistrationDate(),
                pending.getClassification(),
                pending.getCompanyStatus(),
                pending.getCompanyEmail(),
                pending.getHeadOfficeAddress(),
                pending.getBranchAddress(),
                pending.getAddressLine2(),
                pending.getState(),
                pending.getCity(),
                pending.getLga(),
                pending.getPostCode(),
                pending.getCountry(),
                pending.getShareCapital(),
                pending.getShareCapitalInWords(),
                pending.getNumberOfAffiliates()
        );
    }
}
