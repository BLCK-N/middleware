package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.MaritalStatus;
import com.myrtle.middleware.enums.Sex;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PersonalInformationRequest {
    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @NotNull(message = "Sex is required")
    private Sex sex;

    private LocalDate dateOfBirth;

    @NotNull(message = "Marital status is required")
    private MaritalStatus maritalStatus;

    @NotBlank(message = "Occupation or business is required")
    @Size(max = 200, message = "Occupation or business must be at most 200 characters")
    private String occupationOrBusiness;

    @Size(max = 100, message = "Income range must be at most 100 characters")
    String incomeRange;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must be at most 100 characters")
    private String city;

    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must be at most 100 characters")
    private String country;

    @Email(message = "Email must be valid")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Phone number must be 10-15 digits (optional + prefix)")
    private String phoneNumber;
}
