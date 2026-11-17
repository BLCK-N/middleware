package com.myrtle.middleware.repository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
public record NinVerificationRequest(

        @NotBlank(message = "NIN (id number) is required")
        @JsonProperty("idNumber")
        String idNumber,

        @NotBlank(message = "First name is required")
        @JsonProperty("firstname")
        String firstname,

        @NotBlank(message = "Last name is required")
        @JsonProperty("lastname")
        String lastname
) {}
