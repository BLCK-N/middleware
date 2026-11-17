package com.myrtle.middleware.dtos.request;

import jakarta.validation.constraints.NotBlank;

public record MemartFileRequest(
        @NotBlank(message = "URL is required")
        String url
) {
}
