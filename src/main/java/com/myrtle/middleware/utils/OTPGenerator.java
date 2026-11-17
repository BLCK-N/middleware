package com.myrtle.middleware.utils;

import lombok.RequiredArgsConstructor;

import java.security.SecureRandom;

@RequiredArgsConstructor
public class OTPGenerator {

    private static final SecureRandom random = new SecureRandom();
    private static final int OTP_LENGTH = 6;

    public static String generateOtp() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < OTP_LENGTH; i++) {
            sb.append(random.nextInt(10)); // generates digit from 0 to 9
        }
        return sb.toString();
    }

    public static String generateOtp(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
}
