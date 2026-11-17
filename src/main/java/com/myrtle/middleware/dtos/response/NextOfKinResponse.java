package com.myrtle.middleware.dtos.response;

import java.time.LocalDateTime;

public record NextOfKinResponse(
        String id,
        String userId,
        String fullName,
        String relationship,
        String phoneNumber,
        String email,
        String address,
        boolean isPrimary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
