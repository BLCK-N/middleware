package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Attaches an additional login (e.g. company secretary, board chairman) to an existing corporate
 * account. Requires the short-lived {@code lookupToken} issued by the corporate lookup endpoint,
 * which proves the caller supplied a matching RC number and registered BVN.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateCorporateUserRequest(

        @NotBlank(message = "Lookup token is required")
        String lookupToken,

        /** Optional - when supplied, must match the company identified by the lookup token. */
        String refCode,

        /** Optional - when supplied, must match the company identified by the lookup token. */
        String rcNumber,

        @NotBlank(message = "First name is required")
        @Size(min = 2, max = 50, message = "First name must be 2–50 characters")
        @Pattern(regexp = "^[A-Za-z\\-\\s']+$", message = "First name: use only letters, spaces, hyphens, apostrophes")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 2, max = 50, message = "Last name must be 2–50 characters")
        @Pattern(regexp = "^[A-Za-z\\-\\s']+$", message = "Last name: use only letters, spaces, hyphens, apostrophes")
        String lastName,

        @Size(max = 50, message = "Middle name must be at most 50 characters")
        @Pattern(regexp = "^[A-Za-z\\-\\s']*$", message = "Middle name: use only letters, spaces, hyphens, apostrophes")
        String middleName,

        @NotBlank(message = "Email address is required")
        @Size(max = 100, message = "Email address must be at most 100 characters")
        @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "Email address must be a valid email")
        String emailAddress,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number must be 10–15 digits (optional + prefix)")
        String phoneNumber,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 64, message = "Password must be 8–64 characters")
        String password,

        @NotBlank(message = "PIN is required")
        @Pattern(regexp = "^\\d{4}$", message = "PIN must be exactly 4 digits")
        String pin,

        @NotBlank(message = "OTP is required")
        String otp,

        @NotBlank(message = "OTP reference is required")
        String otpReference,

        @NotBlank(message = "BVN is required")
        @Pattern(regexp = "^[0-9]{11}$", message = "BVN must be exactly 11 digits")
        String bvn,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        @Pattern(regexp = "^[A-Za-z0-9,\\.\\-\\s#/]+$", message = "Address: use letters, numbers, and common punctuation only")
        String address,

        @Pattern(regexp = "^(male|female)$",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Gender must be male or female")
        String gender,

        boolean isPep,
        String pepPosition,

        /** Optional - corporate users cannot withdraw, so next of kin is not required. */
        @Size(max = 3, message = "Next of kin: at most 3 contacts allowed")
        List<@Valid NextOfKinRequest> nextOfKins
) {
}
