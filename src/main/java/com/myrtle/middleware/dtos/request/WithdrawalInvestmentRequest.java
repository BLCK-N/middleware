package com.myrtle.middleware.dtos.request;

public record WithdrawalInvestmentRequest(
        String pin,
        String productId,
        String amount
) {
}
