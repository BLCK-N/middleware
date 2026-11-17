package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.myrtle.middleware.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BasicDetails {

    @NotBlank(message = "First name is required")
    @Size(max = 200, message = "First name must be at most 200 characters")
    private String firstName;

    @Size(max = 200, message = "Middle name must be at most 200 characters")
    private String middleName;

    @NotBlank(message = "Last name is required")
    @Size(max = 200, message = "Last name must be at most 200 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Size(max = 200, message = "Email must be at most 200 characters")
    private String email;

    @NotBlank(message = "Phone Number is required")
    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number must be 10-15 digits (optional + prefix)")
    private String phoneNumber;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @Pattern(regexp = "^(|[A-Za-z0-9]{4,20})$", message = "inviteCode: use 4-20 letters or numbers")
    private String inviteCode;
}
