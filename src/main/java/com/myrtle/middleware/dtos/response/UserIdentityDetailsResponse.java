package com.myrtle.middleware.dtos.response;

import java.util.List;

public record UserIdentityDetailsResponse(
        String address,
        String nin,
        String bvn,
        List<NextOfKinResponse> nextOfKins
) {
}
