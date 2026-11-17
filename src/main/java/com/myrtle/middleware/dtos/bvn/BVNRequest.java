package com.myrtle.middleware.dtos.bvn;

import jakarta.validation.constraints.NotBlank;

public record BVNRequest(
        @NotBlank(message = "BVN is required") String bvn,
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        @NotBlank(message = "Phone number is required") String phoneNumber,
        @NotBlank(message = "Email is required") String email,
        @NotBlank(message = "Gender is required") String gender,
        boolean newUser
) {
}
