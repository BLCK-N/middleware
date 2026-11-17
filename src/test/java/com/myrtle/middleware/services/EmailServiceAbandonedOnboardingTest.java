package com.myrtle.middleware.services;

import com.myrtle.middleware.model.PendingOnboarding;
import com.myrtle.middleware.model.PendingOnboarding.ExitReason;
import com.myrtle.middleware.model.PendingOnboarding.Stage;
import com.myrtle.middleware.model.PendingOnboarding.Status;
import com.myrtle.middleware.model.PendingOnboarding.Type;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceAbandonedOnboardingTest {

    @Mock private ExternalAPIs externalAPIs;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(externalAPIs);
        ReflectionTestUtils.setField(emailService, "host", "https://api.example.com");
        ReflectionTestUtils.setField(emailService, "frontendUrl", "https://app.example.com");
        ReflectionTestUtils.setField(emailService, "supportEmail", "wecare@myrtleng.com");
        ReflectionTestUtils.setField(emailService, "supportPhone", "02018888888");
        ReflectionTestUtils.setField(emailService, "onboardingInternalNotifyEmail", "wecare@myrtleng.com");
    }

    @Test
    void customerReminderLinksToExistingAppAndDoesNotClaimAccountExists() {
        PendingOnboarding record = sample();

        emailService.sendAbandonedOnboardingReminder(record);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(externalAPIs).sendTransactionalEmailStrict(
                eq("alex@example.com"),
                eq("Continue Your Myrtle Account Opening"),
                body.capture()
        );
        String html = body.getValue();
        assertThat(html).contains("Continue Application");
        assertThat(html).contains("https://app.example.com");
        assertThat(html).contains("WHAT YOU CAN DO NEXT");
        assertThat(html).contains("The Myrtle Team");
        assertThat(html).contains("started opening an account with");
        assertThat(html).doesNotContain("/onboarding/continue");
        assertThat(html).doesNotContain("token=");
        assertThat(html).contains("wecare@myrtleng.com");
        assertThat(html).contains("02018888888");
        assertThat(html).contains("Alex");
        assertThat(html).contains("have not finished creating it yet");
        assertThat(html).doesNotContain("account has been created");
        assertThat(html).doesNotContain("created an account with");
        assertThat(html).doesNotContain("backoffice.example.com");
    }

    @Test
    void internalAlertContainsCustomerOnboardingDetailsForOpsFollowUp() {
        PendingOnboarding record = sample();

        emailService.sendAbandonedOnboardingInternalAlert(record);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(externalAPIs).sendTransactionalEmailStrict(
                eq("wecare@myrtleng.com"),
                eq("Alex Okeke — Incomplete account opening"),
                body.capture()
        );
        String html = body.getValue();
        assertThat(html).contains("Operations Notification");
        assertThat(html).contains("Alex Okeke");
        assertThat(html).contains("alex@example.com");
        assertThat(html).contains("2348012345678");
        assertThat(html).contains("OTP Verification");
        assertThat(html).contains("BVN Verification");
        assertThat(html).contains("UNKNOWN");
        assertThat(html).contains("has not completed account creation");
        assertThat(html).doesNotContain("backoffice.example.com");
        assertThat(html).doesNotContain("token=");
        assertThat(html).doesNotContain("New account registration");
    }

    private PendingOnboarding sample() {
        PendingOnboarding record = new PendingOnboarding();
        record.setId("po-1");
        record.setEmail("alex@example.com");
        record.setFirstName("Alex");
        record.setLastName("Okeke");
        record.setPhoneNumber("2348012345678");
        record.setOnboardingType(Type.INDIVIDUAL);
        record.setLastCompletedStage(Stage.OTP_VERIFIED);
        record.setCurrentStage(Stage.BVN);
        record.setStatus(Status.PENDING_ONBOARDING);
        record.setExitReason(ExitReason.UNKNOWN);
        record.setStartedAt(LocalDateTime.of(2026, 8, 28, 10, 30));
        record.setLastActivityAt(LocalDateTime.of(2026, 8, 28, 11, 45));
        return record;
    }
}
