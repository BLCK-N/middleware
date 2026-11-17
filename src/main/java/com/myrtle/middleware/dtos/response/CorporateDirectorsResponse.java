package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;

import java.util.List;

/**
 * What page 6 (directors) currently holds. Directors are stored as a set - resubmitting the page
 * replaces the whole list - so this returns the list as a whole, with BVNs masked the same way the
 * review screen sees them.
 */
public record CorporateDirectorsResponse(
        boolean collected,
        int count,
        List<CorporateOnboardingPreviewResponse.DirectorPreview> directors
) {

    public static CorporateDirectorsResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }
        List<CorporateOnboardingPreviewResponse.DirectorPreview> directors =
                pending.getDirectors() == null
                        ? List.of()
                        : pending.getDirectors().stream()
                        .map(CorporateOnboardingPreviewResponse.DirectorPreview::from)
                        .toList();

        return new CorporateDirectorsResponse(!directors.isEmpty(), directors.size(), directors);
    }
}
