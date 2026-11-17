package com.myrtle.middleware.dtos.request;

import lombok.Builder;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;


@Builder
public record FunctionResponseForStats(
        boolean isSuccess,
        HttpStatus status,
        String message,
        String totalBalance,
        Object data
) {
}
