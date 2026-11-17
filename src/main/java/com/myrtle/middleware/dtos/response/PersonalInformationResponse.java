package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.MaritalStatus;
import com.myrtle.middleware.enums.Sex;

import java.time.LocalDate;

public record PersonalInformationResponse(
        String name,
        Sex sex,
        LocalDate dateOfBirth,
        MaritalStatus maritalStatus,
        String occupationOrBusiness,
        String incomeRange,
        String city,
        String country,
        String email,
        String phoneNumber
) {
}
