package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CorporateCredentialsRequest(

        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
        String pin,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Profession is required")
        String profession,

        @NotBlank(message = "Nationality is required")
        String nationality,

        @NotBlank(message = "ID expiry date is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "ID expiry date must use yyyy-MM-dd format")
        String idExpDate,

        /** Optional; defaults to NGN when blank. */
        @Pattern(regexp = "^[A-Z]{3}$", message = "Valuation currency must be a 3-letter ISO code")
        String valuationCurrency,

        /** Optional; inferred from CAC company type when blank. */
        String businessStructure,

        String username,

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
}
