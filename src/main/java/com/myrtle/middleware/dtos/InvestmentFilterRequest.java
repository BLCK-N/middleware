package com.myrtle.middleware.dtos;

import com.myrtle.middleware.model.Investment;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
public class InvestmentFilterRequest {

    private String userId;
    private Investment.OrderType orderType;
    private Investment.Currency currency;
    private Investment.InvestmentStatus status;
    private String search;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdAfter;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime createdBefore;
}
