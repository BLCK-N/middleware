package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record NextOfKinRequest(
        @NotBlank(message = "NOK full name is required")
        @Size(max = 300, message = "NOK full name must be at most 300 characters")
        @Pattern(regexp = "^(?=.{1,255}$)[^\\x00-\\x1F\\x7F\\r\\n]+$", message = "NOK full name: use only letters, spaces, hyphens, apostrophes")
        String fullName,

        @NotBlank(message = "NOK relationship is required")
        @Pattern(regexp = "^[A-Za-z\\-\\s']+$", message = "NOK relationship: use only letters, spaces, hyphens, apostrophes")
        String relationship,

        @NotBlank(message = "NOK phone number is required")
        @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "NOK phone number must be 10–15 digits (optional + prefix)")
        String phoneNumber,

        @Size(max = 150, message = "NOK email must be at most 150 characters")
        @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "NOK email must be a valid email address")
        String email,

        @NotBlank(message = "NOK address is required")
        @Size(max = 255, message = "NOK address must be at most 255 characters")
        @Pattern(regexp = "^(?=.{1,255}$)[^\\x00-\\x1F\\x7F\\r\\n]+$", message = "NOK address: use letters, numbers, and common punctuation only")
        String address,
        Boolean isPrimary
) {
}
