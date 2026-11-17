package com.myrtle.middleware.dtos.request;

import lombok.Builder;
import org.springframework.http.HttpStatus;


@Builder
public record FunctionResponse(
        boolean isSuccess,
        HttpStatus status,
        String message,
        Object data
) {
}
