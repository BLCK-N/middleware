package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.utils.MaskUtil;
import org.springframework.util.StringUtils;

/**
 * What page 2 (BVN) currently holds. The BVN itself is masked - a stored BVN is changed by
 * resubmitting it through the verification step, never by editing the value that comes back here.
 * {@code dateOfBirth} and {@code maritalStatus} are the fields the BVN lookup filled in.
 */
public record CorporateBvnDetailsResponse(
        boolean collected,
        boolean verified,
        String maskedBvn,
        String middleName,
        String dateOfBirth,
        String maritalStatus
) {

    public static CorporateBvnDetailsResponse from(PendingCorporateData pending) {
        if (pending == null) {
            return null;
        }
        boolean collected = StringUtils.hasText(pending.getBvn());
        return new CorporateBvnDetailsResponse(
                collected,
                collected,
                MaskUtil.maskTail(pending.getBvn(), 2),
                pending.getMiddleName(),
                pending.getDate(),
                pending.getMaritalStatus()
        );
    }
}
