package com.myrtle.middleware.dtos.request;

public record EditProduct(
        String initialLimit,
        String minimumLimit
) {
}
