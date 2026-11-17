package com.myrtle.middleware.dtos.referral.mywealth.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDistributionResponse(List<Distribution> content) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Distribution(String currency, BigDecimal amount, LocalDate distributionDate) {
    }
}
