package com.myrtle.middleware.dtos.request;

public record PinRequest(
        String pin,
        boolean usePin
) {
}
