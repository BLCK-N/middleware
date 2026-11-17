package com.myrtle.middleware.dtos.request;

import lombok.Builder;
import org.springframework.http.HttpStatus;


@Builder
public record ViewSingleProductResponse(
        boolean isSuccess,
        HttpStatus status,
        String message,
        boolean isUserInitialInvestment,
        String totalInvestmentAmount,
        Object data
) {
}
