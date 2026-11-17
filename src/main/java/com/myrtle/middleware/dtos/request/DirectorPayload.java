package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Director details collected during corporate onboarding.
 * Optional fields support mapping into MyWealth ContactPayload without breaking older clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record DirectorPayload(

        @NotBlank(message = "First name is required")
        @Size(max = 80, message = "First name must be at most 80 characters")
        String firstName,

        @Size(max = 80, message = "Middle name must be at most 80 characters")
        String middleName,

        @NotBlank(message = "Last name is required")
        @Size(max = 80, message = "Last name must be at most 80 characters")
        String lastName,

        @NotBlank(message = "Email address is required")
        @Email(message = "Email address must be valid")
        @Size(max = 150, message = "Email address must be at most 150 characters")
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number must be 10-15 digits (optional + prefix)")
        String phoneNumber,

        @NotBlank(message = "BVN is required")
        @Pattern(regexp = "^[0-9]{11}$", message = "BVN must be exactly 11 digits")
        String bvn,

        /** Defaults to CORP_DIRECTOR when blank. */
        String role,

        /** When true, this director is also emitted as a MyWealth BENEFICIAL_OWNER. */
        Boolean beneficialOwner,

        String gender,
        String title,

        @Size(max = 150, message = "Address line 1 must be at most 150 characters")
        String addressLine1,

        @Size(max = 150, message = "Address line 2 must be at most 150 characters")
        String addressLine2,

        @Size(max = 100, message = "City must be at most 100 characters")
        String city,

        @Size(max = 100, message = "State must be at most 100 characters")
        String state,

        @Size(max = 20, message = "Post code must be at most 20 characters")
        String postCode,

        @Size(max = 100, message = "Country must be at most 100 characters")
        String country,

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Birth date must use yyyy-MM-dd format")
        String birthDate,

        String maritalStatus
) {
}
