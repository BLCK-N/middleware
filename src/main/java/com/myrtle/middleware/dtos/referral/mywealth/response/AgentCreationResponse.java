package com.myrtle.middleware.dtos.referral.mywealth.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;


@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentCreationResponse(
        String id,
        String code,
        String refCode,
        String type,
        String label,
        String email,
        Long mobileNo,
        String groupId,
        String status,
        Boolean payoutEnabled,
        Double payoutRateAnnual,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
