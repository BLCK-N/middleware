package com.myrtle.middleware.dtos.request;

public record TerminateBulkPayload(
        String pin,
        String productId
) {
}
