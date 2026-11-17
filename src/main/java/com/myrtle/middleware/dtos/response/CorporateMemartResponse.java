package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;
import org.springframework.util.StringUtils;

/**
 * What page 5 (MEMART) currently holds - the URL of the uploaded document, so the frontend can
 * show "already uploaded" instead of an empty file picker.
 */
public record CorporateMemartResponse(
        boolean collected,
        String url
) {

    public static CorporateMemartResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }
        return new CorporateMemartResponse(
                StringUtils.hasText(pending.getMemartUrl()),
                pending.getMemartUrl()
        );
    }
}
