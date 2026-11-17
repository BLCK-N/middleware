package com.myrtle.middleware.dtos.request;

public record EditUserRequest(
        String firstname,
        String lastname,
        String middleName,
        String reason,
        String phoneNumber,
        String[] imageProof
) {
}
