package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MyWealthAddressResponse(
        String id,
        String type,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postCode,
        String country
) {
}
