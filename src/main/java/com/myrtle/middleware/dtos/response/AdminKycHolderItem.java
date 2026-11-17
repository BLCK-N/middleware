package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.Status;

/**
 * Safe projection of {@link com.myrtle.middleware.model.HoldPersonalDetails} for admin KYC list/detail.
 */
public record AdminKycHolderItem(
        String id,
        String userId,
        String userEmail,
        String firstName,
        String lastName,
        String middleName,
        String phoneNumber,
        String reasonForChange,
        String[] proofImage,
        Status status
) {
}
