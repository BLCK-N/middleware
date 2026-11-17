package com.myrtle.middleware.model;

public record UserProfileDto(
        String id,
        String firstName,
        String lastName,
        String middleName,
        String emailAddress,
        String phoneNumber,
        String clientId,
        String branchId,
        String accountNumber,
        String investmentAccountId,
        String role,
        String tier,
        String gender,
        String address,
        boolean usePin,
        String lastBalance,
        String profileImage,
        boolean isPep,
        String pepPosition
) {}

