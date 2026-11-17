package com.myrtle.middleware.dtos.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyDto(

        @NotBlank(message = "Email is required")
        @Size(max = 100, message = "Email too long")
        @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "valid email must be provided")
        String email,

        @NotBlank(message = "otp is required")
        String otp,

        @NotBlank(message = "otpReference is required")
        String otpReference
) {
}
