package com.myrtle.middleware.dtos.request;

public record TransferOutRequest(
        String accountId,
        String amount
) {
}
