package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;

/**
 * Everything the user has submitted so far, grouped by the page that collected it - the read
 * counterpart of the whole onboarding flow, for a review screen or for restoring a session that
 * was abandoned partway through.
 *
 * <p>It carries the same fields as the per-page endpoints, so a client can fetch this once instead
 * of calling six endpoints. Credentials remain write-only here too: the PIN and password are
 * reported as set/not set, and BVNs are masked.</p>
 */
public record CorporateOnboardingSubmissionResponse(
        String email,
        boolean emailVerified,
        int nextPage,
        CorporateBasicDetailsResponse basicDetails,
        CorporateBvnDetailsResponse bvn,
        CorporateKybDetailsResponse kyb,
        CorporateMemartResponse memart,
        CorporateDirectorsResponse directors,
        CorporateCredentialsDetailsResponse credentials,
        CorporateOnboardingProgressResponse progress
) {

    public static CorporateOnboardingSubmissionResponse from(PendingCorporateData pending, int nextPage) {
        if (pending == null) {
            return null;
        }
        return new CorporateOnboardingSubmissionResponse(
                pending.getEmail(),
                pending.isEmailVerified(),
                nextPage,
                CorporateBasicDetailsResponse.from(pending),
                CorporateBvnDetailsResponse.from(pending),
                CorporateKybDetailsResponse.from(pending),
                CorporateMemartResponse.from(pending),
                CorporateDirectorsResponse.from(pending),
                CorporateCredentialsDetailsResponse.from(pending),
                CorporateOnboardingProgressResponse.from(pending)
        );
    }
}
