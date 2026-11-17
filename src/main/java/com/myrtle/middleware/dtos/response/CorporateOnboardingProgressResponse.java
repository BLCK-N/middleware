package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.utils.MaskUtil;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * What the app needs to render onboarding progress - without echoing the full staged record (BVN,
 * director details) back over the wire on every step.
 */
public record CorporateOnboardingProgressResponse(
        String email,
        String companyName,
        String rcNumber,
        String maskedBvn,
        boolean emailVerified,
        boolean cacVerified,
        boolean memartCollected,
        int directorsCollected,
        boolean credentialsCollected,
        List<String> outstandingSteps
) {

    public static CorporateOnboardingProgressResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }

        int directorCount = pending.getDirectors() == null ? 0 : pending.getDirectors().size();
        boolean memartCollected = StringUtils.hasText(pending.getMemartUrl());
        boolean credentialsCollected =
                StringUtils.hasText(pending.getPin()) && StringUtils.hasText(pending.getPassword());

        return new CorporateOnboardingProgressResponse(
                pending.getEmail(),
                pending.getCompanyName(),
                pending.getRcNumber(),
                MaskUtil.maskTail(pending.getBvn(), 2),
                pending.isEmailVerified(),
                pending.isCacVerified(),
                memartCollected,
                directorCount,
                credentialsCollected,
                outstandingSteps(pending, memartCollected, directorCount, credentialsCollected)
        );
    }

    /**
     * Mirrors the gates enforced by the completion step, so the app can tell the user what is left.
     */
    private static List<String> outstandingSteps(
            PendingCorporateData pending,
            boolean memartCollected,
            int directorCount,
            boolean credentialsCollected
    ) {
        List<String> outstanding = new ArrayList<>();

        if (!pending.isEmailVerified() || !StringUtils.hasText(pending.getBvn())) {
            outstanding.add("KYC");
        }
        if (!pending.isCacVerified() || !StringUtils.hasText(pending.getRcNumber())) {
            outstanding.add("KYB");
        }
        if (!memartCollected) {
            outstanding.add("MEMART");
        }
        if (directorCount == 0) {
            outstanding.add("DIRECTORS");
        }
        if (!credentialsCollected) {
            outstanding.add("CREDENTIALS");
        }

        return outstanding;
    }
}
