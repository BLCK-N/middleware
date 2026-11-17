package com.myrtle.middleware.dtos.response;

public record TerminateRequest(
        String investmentId,
        String pin,
        String amount
) {
}
