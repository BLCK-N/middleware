package com.myrtle.middleware.dtos.referral.mywealth.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentBalanceResponse(
//        String id,
        String programId,
//        String subjectType,
//        Long whSubjectId,
        String currency,
        BigDecimal balance
//        BigDecimal lastPrincipal,
//        LocalDate lastAccrualDate
) {
}
