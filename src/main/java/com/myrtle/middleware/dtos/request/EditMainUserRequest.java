package com.myrtle.middleware.dtos.request;

public record EditMainUserRequest(
        String firstName,
        String lastName,
        String middleName,
        String email
) {
}
