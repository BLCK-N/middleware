package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidteOtpForCorporate {
    @NotBlank(message = "OTP is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 characters")
    private String otp;

    @NotBlank(message = "OTP reference is required")
    private String otpReference;

    @NotBlank(message = "Email is required")
    @Email(message = "Kindly provide a valid email")
    private String email;
}
