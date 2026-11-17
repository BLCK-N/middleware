package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;
import org.springframework.util.StringUtils;

/**
 * What page 1 (basic details) currently holds, so the frontend can re-render the form pre-filled
 * when the user navigates back or resumes onboarding on another device.
 */
public record CorporateBasicDetailsResponse(
        boolean collected,
        String firstName,
        String middleName,
        String lastName,
        String email,
        boolean emailVerified,
        String phoneNumber,
        String gender
) {

    public static CorporateBasicDetailsResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }
        return new CorporateBasicDetailsResponse(
                StringUtils.hasText(pending.getFirstName()),
                pending.getFirstName(),
                pending.getMiddleName(),
                pending.getLastName(),
                pending.getEmail(),
                pending.isEmailVerified(),
                pending.getPhoneNumber(),
                pending.getGender() == null ? null : pending.getGender().name()
        );
    }
}
