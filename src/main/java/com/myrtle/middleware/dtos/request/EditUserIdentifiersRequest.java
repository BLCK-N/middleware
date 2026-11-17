package com.myrtle.middleware.dtos.request;

public record EditUserIdentifiersRequest(
        String clientId,
        String investmentAccountId
) {
}