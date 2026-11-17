package com.myrtle.middleware.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A verified OTP proves someone reached that inbox at a point in time. These pin how long that
 * remains true for the purpose of gating account creation.
 */
class UserOtpVerificationFreshnessTest {

    private UserOtp otp() {
        UserOtp otp = new UserOtp();
        otp.setEmail("owner@company.com");
        otp.setOtp("hashed");
        return otp;
    }

    @Test
    void markVerifiedRecordsWhenItHappened() {
        UserOtp otp = otp();

        otp.markVerified();

        assertThat(otp.isVerified()).isTrue();
        assertThat(otp.getTimeUsed()).isNotNull();
        assertThat(otp.isVerificationFresh()).isTrue();
    }

    @Test
    void anUnverifiedOtpIsNeverFresh() {
        assertThat(otp().isVerificationFresh()).isFalse();
    }

    @Test
    void aVerificationFromWithinTheWindowStillCounts() {
        UserOtp otp = otp();
        otp.setVerified(true);
        otp.setTimeUsed(LocalDateTime.now().minusHours(23));

        assertThat(otp.isVerificationFresh()).isTrue();
    }

    @Test
    void aVerificationOlderThanTheWindowDoesNot() {
        UserOtp otp = otp();
        otp.setVerified(true);
        otp.setTimeUsed(LocalDateTime.now().minusHours(25));

        assertThat(otp.isVerificationFresh()).isFalse();
    }

    @Test
    void aLegacyRowWithNoTimeUsedFailsClosed() {
        // rows verified before timeUsed was recorded must not grant indefinite access
        UserOtp otp = otp();
        otp.setVerified(true);
        otp.setTimeUsed(null);

        assertThat(otp.isVerified()).isTrue();
        assertThat(otp.isVerificationFresh()).isFalse();
    }

    @Test
    void issuanceExpiryIsSeparateFromVerificationFreshness() {
        UserOtp otp = otp();
        otp.setTimeGenerated(LocalDateTime.now().minusMinutes(10));

        // the 5-minute issuance TTL has passed...
        assertThat(otp.isExpired()).isTrue();
        // ...and it was never verified, so it is not fresh either
        assertThat(otp.isVerificationFresh()).isFalse();
    }
}
