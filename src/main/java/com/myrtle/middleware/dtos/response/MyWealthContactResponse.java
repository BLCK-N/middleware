package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MyWealthContactResponse(
        String id,
        String role,
        String refCode,
        String label,
        String title,
        String firstName,
        String middleName,
        String lastName,
        String email,
        String mobileNo,
        String username,
        Boolean grantLoginAccess,
        LocalDate birthDate,
        String idNo,
        String finIdNo,
        String idType,
        String riskRating,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
