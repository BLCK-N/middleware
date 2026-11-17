package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MyWealthClientResponse(
        String id,
        String code,
        String refCode,
        String clientType,
        String businessStructure,
        String label,
        String email,
        String notificationEmail,
        String mobileNo,
        String valuationCurrency,
        String groupId,
        String status,
        String kycStatus,
        Boolean notificationEnabled,
        Boolean deleted,
        LocalDate businessIncDate,
        LocalDate relationshipStartDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<MyWealthAddressResponse> address,
        List<MyWealthContactResponse> contact
) {
}
