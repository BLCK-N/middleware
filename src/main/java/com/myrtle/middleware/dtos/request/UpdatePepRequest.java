package com.myrtle.middleware.dtos.request;

public record UpdatePepRequest(
        boolean isPep,
        String pepPosition
) {
}