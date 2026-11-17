package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.model.PendingOnboarding;
import com.myrtle.middleware.model.PendingOnboarding.ExitReason;
import com.myrtle.middleware.model.PendingOnboarding.Stage;
import com.myrtle.middleware.model.PendingOnboarding.Status;
import com.myrtle.middleware.model.PendingOnboarding.Type;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.PendingOnboardingRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingOnboardingServiceTest {

    private static final String EMAIL = "alex@example.com";

    @Mock private PendingOnboardingRepository pendingOnboardingRepository;
    @Mock private UserRepository userRepository;
    @Mock private CorporateClientRepository corporateClientRepository;
    @Mock private EmailService emailService;

    private PendingOnboardingService service;

    @BeforeEach
    void setUp() {
        service = new PendingOnboardingService(
                pendingOnboardingRepository,
                userRepository,
                corporateClientRepository,
                emailService
        );
        ReflectionTestUtils.setField(service, "abandonmentThreshold", Duration.ofHours(24));
        ReflectionTestUtils.setField(service, "batchSize", 100);
        ReflectionTestUtils.setField(service, "schedulerEnabled", true);
        when(pendingOnboardingRepository.save(any(PendingOnboarding.class))).thenAnswer(invocation -> {
            PendingOnboarding record = invocation.getArgument(0);
            if (record.getId() == null) {
                record.setId("po-1");
            }
            return record;
        });
    }

    @Test
    void otpVerificationStartsOnboarding() {
        stubNoExistingAccount();
        when(pendingOnboardingRepository
                .findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                        EMAIL, Type.INDIVIDUAL, Status.ACCOUNT_CREATED))
                .thenReturn(Optional.empty());

        PendingOnboarding started = service.startForTest(EMAIL, "otp-ref-1", Type.INDIVIDUAL).orElseThrow();

        assertThat(started.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(started.getLastCompletedStage()).isEqualTo(Stage.OTP_VERIFIED);
        assertThat(started.getCurrentStage()).isEqualTo(Stage.BVN);
        assertThat(started.getStartedAt()).isNotNull();
    }

    @Test
    void completingBvnPersistsProgress() {
        PendingOnboarding existing = active();
        stubActiveLookup(existing);

        service.recordIndividualBvnVerified(EMAIL, "Alex", "Okeke", "2348012345678");

        assertThat(existing.getLastCompletedStage()).isEqualTo(Stage.BVN);
        assertThat(existing.getCurrentStage()).isEqualTo(Stage.ACCOUNT_CREATION);
        assertThat(existing.getFirstName()).isEqualTo("Alex");
    }

    @Test
    void inactiveOnboardingIsMarkedPending() {
        PendingOnboarding existing = active();
        when(pendingOnboardingRepository.findById("po-1")).thenReturn(Optional.of(existing));

        PendingOnboarding abandoned = service.markAbandoned("po-1");

        assertThat(abandoned.getStatus()).isEqualTo(Status.PENDING_ONBOARDING);
        assertThat(abandoned.getExitReason()).isEqualTo(ExitReason.UNKNOWN);
    }

    @Test
    void accountCreationClosesPendingOnboarding() {
        PendingOnboarding existing = active();
        stubActiveLookup(existing);

        service.markAccountCreated(EMAIL, Type.INDIVIDUAL, "user-99");

        assertThat(existing.getStatus()).isEqualTo(Status.ACCOUNT_CREATED);
        assertThat(existing.getCurrentStage()).isEqualTo(Stage.ACCOUNT_CREATED);
    }

    @Test
    void existingCustomerDoesNotCreatePendingOnboarding() {
        User user = new User();
        user.setEmailAddress(EMAIL);
        when(userRepository.findByEmailAddress(EMAIL)).thenReturn(Optional.of(user));

        assertThat(service.startForTest(EMAIL, "otp-ref", Type.INDIVIDUAL)).isEmpty();
        verify(pendingOnboardingRepository, never()).save(any());
    }

    @Test
    void duplicateOnboardingReusesExistingRecord() {
        PendingOnboarding existing = active();
        stubNoExistingAccount();
        when(pendingOnboardingRepository
                .findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                        EMAIL, Type.INDIVIDUAL, Status.ACCOUNT_CREATED))
                .thenReturn(Optional.of(existing));

        PendingOnboarding reused = service.startForTest(EMAIL, "new-ref", Type.INDIVIDUAL).orElseThrow();

        assertThat(reused.getId()).isEqualTo("po-1");
        assertThat(reused.getOtpReference()).isEqualTo("new-ref");
    }

    @Test
    void returningThroughExistingOtpReopensInProgress() {
        PendingOnboarding existing = pending();
        stubNoExistingAccount();
        when(pendingOnboardingRepository
                .findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                        EMAIL, Type.INDIVIDUAL, Status.ACCOUNT_CREATED))
                .thenReturn(Optional.of(existing));

        PendingOnboarding reused = service.startForTest(EMAIL, "otp-ref-2", Type.INDIVIDUAL).orElseThrow();

        assertThat(reused.getStatus()).isEqualTo(Status.IN_PROGRESS);
        assertThat(reused.getLastCompletedStage()).isEqualTo(Stage.OTP_VERIFIED);
    }

    @Test
    void followUpSendsCustomerAndInternalEmails() {
        PendingOnboarding record = pending();
        when(pendingOnboardingRepository.findById("po-1")).thenReturn(Optional.of(record));

        assertThat(service.followUp("po-1")).isTrue();
        verify(emailService).sendAbandonedOnboardingReminder(record);
        verify(emailService).sendAbandonedOnboardingInternalAlert(record);
        verify(pendingOnboardingRepository, org.mockito.Mockito.atLeastOnce()).save(record);
    }

    @Test
    void reminderIsNotSentAgainWhenAlreadyReminderSent() {
        PendingOnboarding record = pending();
        record.setStatus(Status.REMINDER_SENT);
        when(pendingOnboardingRepository.findById("po-1")).thenReturn(Optional.of(record));

        assertThat(service.followUp("po-1")).isFalse();
        verify(emailService, never()).sendAbandonedOnboardingReminder(any());
    }

    @Test
    void emailFailureLeavesOnboardingPending() {
        PendingOnboarding record = pending();
        when(pendingOnboardingRepository.findById("po-1")).thenReturn(Optional.of(record));
        doThrow(new IllegalStateException("SES down"))
                .when(emailService).sendAbandonedOnboardingReminder(any());
        doThrow(new IllegalStateException("SES down"))
                .when(emailService).sendAbandonedOnboardingInternalAlert(any());

        assertThat(service.followUp("po-1")).isFalse();
        assertThat(record.getStatus()).isEqualTo(Status.PENDING_ONBOARDING);
    }

    private PendingOnboarding active() {
        PendingOnboarding record = new PendingOnboarding();
        record.setId("po-1");
        record.setEmail(EMAIL);
        record.setOnboardingType(Type.INDIVIDUAL);
        record.setStatus(Status.IN_PROGRESS);
        record.setLastCompletedStage(Stage.OTP_VERIFIED);
        record.setCurrentStage(Stage.BVN);
        record.setStartedAt(LocalDateTime.now().minusHours(1));
        record.setLastActivityAt(LocalDateTime.now().minusMinutes(5));
        record.setCreatedAt(LocalDateTime.now().minusHours(1));
        return record;
    }

    private PendingOnboarding pending() {
        PendingOnboarding record = active();
        record.setStatus(Status.PENDING_ONBOARDING);
        record.setFirstName("Alex");
        record.setLastName("Okeke");
        record.setPhoneNumber("2348012345678");
        record.setExitReason(ExitReason.UNKNOWN);
        return record;
    }

    private void stubNoExistingAccount() {
        when(userRepository.findByEmailAddress(EMAIL)).thenReturn(Optional.empty());
        when(corporateClientRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.empty());
    }

    private void stubActiveLookup(PendingOnboarding existing) {
        stubNoExistingAccount();
        when(pendingOnboardingRepository
                .findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                        EMAIL, Type.INDIVIDUAL, Status.ACCOUNT_CREATED))
                .thenReturn(Optional.of(existing));
    }
}
