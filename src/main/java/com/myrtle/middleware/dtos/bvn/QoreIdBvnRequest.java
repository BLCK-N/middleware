package com.myrtle.middleware.dtos.bvn;

public record QoreIdBvnRequest(
        String bvn,
        String firstName,
        String lastName,
        String phoneNumber
) {
}
