package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.Status;

public record AdminHolderDetailsItem(
        UserInfo user,
        HolderDetailsInfo holderDetails
) {

    public record UserInfo(
            String id,
            String emailAddress,
            String firstName,
            String lastName
    ) {
    }

    public record HolderDetailsInfo(
            HolderDetailInfo holdPersonalDetails,
            HolderDetailInfo addHolderDetails
    ) {
    }

    public record HolderDetailInfo(
            String id,
            Status status
    ) {
    }
}
