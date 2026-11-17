package com.myrtle.middleware.dtos.request;

import com.myrtle.middleware.enums.Status;

public record HoldPersonalDetailsDto(
        String id,
        String firstName,
        String lastName,
        String middleName,
        String phoneNumber,
        String reasonForChange,
        Status status,
        String[] proofImage
) {}

