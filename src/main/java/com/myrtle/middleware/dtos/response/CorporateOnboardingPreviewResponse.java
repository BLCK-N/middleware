package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.PendingCorporateDirector;
import com.myrtle.middleware.utils.MaskUtil;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The full staged onboarding record, for a "review and edit" screen - unlike
 * {@link CorporateOnboardingProgressResponse} (a summary of what's left), this echoes every field
 * collected so far. Password and PIN are never included - they are write-only credentials, not
 * editable form fields - and BVNs are masked, since correcting one means resubmitting it through
 * /validate-bvn or /directors for re-verification, not editing the stored value directly.
 */
public record CorporateOnboardingPreviewResponse(
        String email,
        boolean emailVerified,
        String firstName,
        String middleName,
        String lastName,
        String phoneNumber,
        String gender,
        String maskedBvn,

        String companyName,
        String companyType,
        String businessStructure,
        String rcNumber,
        boolean cacVerified,
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
        Integer numberOfAffiliates,

        String memartUrl,

        List<DirectorPreview> directors,

        String username,
        String valuationCurrency,
        String profession,
        String nationality,
        String netWorth,
        String annualIncome,
        String liquidNetWorth,
        String sourceOfWealth,
        String investmentExperience,
        String riskTolerance,
        String investmentObjective,
        String idType,
        String idExpiry,
        String dateOfBirth,
        String idIssue,
        String issuingAuthority,
        String placeOfIssue,
        String maritalStatus,
        String title,
        String mothersMaidenName,
        String stateOfOrigin,
        String localGovernment,
        String employerName,
        String employerAddress,
        String address
) {

    public record DirectorPreview(
            String firstName,
            String middleName,
            String lastName,
            String email,
            String phoneNumber,
            String maskedBvn,
            String role,
            boolean beneficialOwner,
            String gender,
            String title,
            String addressLine1,
            String addressLine2,
            String city,
            String state,
            String postCode,
            String country,
            String birthDate,
            String maritalStatus
    ) {
        public static DirectorPreview from(PendingCorporateDirector director) {
            return new DirectorPreview(
                    director.getFirstName(),
                    director.getMiddleName(),
                    director.getLastName(),
                    director.getEmail(),
                    director.getPhoneNumber(),
                    MaskUtil.maskTail(director.getBvn(), 2),
                    director.getRole(),
                    director.isBeneficialOwner(),
                    director.getGender(),
                    director.getTitle(),
                    director.getAddressLine1(),
                    director.getAddressLine2(),
                    director.getCity(),
                    director.getState(),
                    director.getPostCode(),
                    director.getCountry(),
                    director.getBirthDate(),
                    director.getMaritalStatus()
            );
        }
    }

    public static CorporateOnboardingPreviewResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }

        List<DirectorPreview> directors = pending.getDirectors() == null
                ? List.of()
                : pending.getDirectors().stream().map(DirectorPreview::from).toList();

        return new CorporateOnboardingPreviewResponse(
                pending.getEmail(),
                pending.isEmailVerified(),
                pending.getFirstName(),
                pending.getMiddleName(),
                pending.getLastName(),
                pending.getPhoneNumber(),
                pending.getGender() == null ? null : pending.getGender().name(),
                MaskUtil.maskTail(pending.getBvn(), 2),

                pending.getCompanyName(),
                pending.getCompanyType(),
                pending.getBusinessStructure(),
                pending.getRcNumber(),
                pending.isCacVerified(),
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
                pending.getNumberOfAffiliates(),

                pending.getMemartUrl(),

                directors,

                pending.getUsername(),
                pending.getValuationCurrency(),
                pending.getProfession(),
                pending.getNationality(),
                pending.getNetWorth(),
                pending.getAnnualIncome(),
                pending.getLiquidNetWorth(),
                pending.getSourceOfWealth(),
                pending.getInvestmentExperience(),
                pending.getRiskTolerance(),
                pending.getInvestmentObjective(),
                pending.getIdType() == null ? null : pending.getIdType().name(),
                pending.getIdExpiry(),
                pending.getDate(),
                pending.getIdIssue(),
                pending.getIssuingAuthority(),
                pending.getPlaceOfIssue(),
                pending.getMaritalStatus(),
                pending.getTitle(),
                pending.getMothersMaidenName(),
                pending.getStateOfOrigin(),
                pending.getLocalGovernment(),
                pending.getEmployerName(),
                pending.getEmployerAddress(),
                pending.getAddress()
        );
    }
}
