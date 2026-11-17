package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAccountRequest(

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

        @NotBlank(message = "BVN is required")
        @Pattern(regexp = "^[0-9]{11}$", message = "BVN must be exactly 11 digits")
        String bvn,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters")
        @Pattern(regexp = "^(?=.{1,255}$)[^\\x00-\\x1F\\x7F\\r\\n]+$", message = "Address: use letters, numbers, and common punctuation only")
        String address,

        @Pattern(regexp = "^(male|female)$",
                flags = Pattern.Flag.CASE_INSENSITIVE,
                message = "Gender must be male or female")
        String gender,
        boolean isPep,
        String pepPosition,

        @NotEmpty(message = "Next of kin: add at least one contact")
        @Size(max = 3, message = "Next of kin: at most 3 contacts allowed")
        List<@Valid NextOfKinRequest> nextOfKins,

        @Pattern(regexp = "^(|[A-Za-z0-9]{4,20})$", message = "inviteCode: use 4-20 letters or numbers")
        String inviteCode,

        @NotBlank(message = "OTP must have been validated before account can be created.")
        String otpReference
) {
}
