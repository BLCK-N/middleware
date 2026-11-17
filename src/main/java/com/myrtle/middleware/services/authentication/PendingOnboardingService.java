package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.PendingOnboarding;
import com.myrtle.middleware.model.PendingOnboarding.ExitReason;
import com.myrtle.middleware.model.PendingOnboarding.Stage;
import com.myrtle.middleware.model.PendingOnboarding.Status;
import com.myrtle.middleware.model.PendingOnboarding.Type;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.PendingOnboardingRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PendingOnboardingService {

    private static final List<Status> FOLLOW_UP_ELIGIBLE = List.of(
            Status.IN_PROGRESS, Status.PENDING_ONBOARDING);

    private final PendingOnboardingRepository pendingOnboardingRepository;
    private final UserRepository userRepository;
    private final CorporateClientRepository corporateClientRepository;
    private final EmailService emailService;

    @Value("${app.onboarding.abandonment-threshold}")
    private Duration abandonmentThreshold;

    @Value("${app.onboarding.scheduler.batch-size:100}")
    private int batchSize;

    @Value("${app.onboarding.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    public void startIndividual(String email, String otpReference) {
        run("individual OTP verified", email, () -> start(email, otpReference, Type.INDIVIDUAL, null));
    }

    public void startCorporate(PendingCorporateData pending) {
        if (pending == null) {
            return;
        }
        run("corporate OTP verified", pending.getEmail(),
                () -> start(pending.getEmail(), null, Type.CORPORATE, pending.getId()));
    }

    public void recordIndividualBvnVerified(String email, String firstName, String lastName, String phoneNumber) {
        run("individual BVN verified", email, () -> {
            PendingOnboarding record = requireActive(email, Type.INDIVIDUAL).orElse(null);
            if (record == null) {
                return;
            }
            record.setFirstName(firstName);
            record.setLastName(lastName);
            record.setPhoneNumber(phoneNumber);
            record.setLastCompletedStage(Stage.BVN);
            record.setCurrentStage(Stage.ACCOUNT_CREATION);
            touch(record);
            appendEvent(record, "ONBOARDING_STAGE_COMPLETED");
            pendingOnboardingRepository.save(record);
        });
    }

    public void recordCorporateProgress(PendingCorporateData pending, int nextPage) {
        if (pending == null) {
            return;
        }
        run("corporate progress", pending.getEmail(), () -> {
            PendingOnboarding record = requireActive(pending.getEmail(), Type.CORPORATE)
                    .orElseGet(() -> start(pending.getEmail(), null, Type.CORPORATE, pending.getId()).orElse(null));
            if (record == null) {
                return;
            }
            record.setApplicationId(pending.getId());
            record.setFirstName(pending.getFirstName());
            record.setLastName(pending.getLastName());
            record.setPhoneNumber(pending.getPhoneNumber());
            record.setLastCompletedStage(Stage.lastCompletedFromCorporatePage(nextPage));
            record.setCurrentStage(Stage.currentFromCorporatePage(nextPage));
            touch(record);
            appendEvent(record, "ONBOARDING_STAGE_COMPLETED");
            pendingOnboardingRepository.save(record);
        });
    }

    public void markAccountCreated(String email, Type type, String userId) {
        run("account created", email, () -> {
            String normalized = normalizeEmail(email);
            if (!StringUtils.hasText(normalized)) {
                return;
            }
            pendingOnboardingRepository
                    .findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                            normalized, type, Status.ACCOUNT_CREATED)
                    .ifPresent(record -> {
                        record.setStatus(Status.ACCOUNT_CREATED);
                        record.setLastCompletedStage(Stage.ACCOUNT_CREATED);
                        record.setCurrentStage(Stage.ACCOUNT_CREATED);
                        record.setUserId(userId);
                        record.setLastActivityAt(LocalDateTime.now());
                        appendEvent(record, "ACCOUNT_CREATED");
                        pendingOnboardingRepository.save(record);
                    });
        });
    }

    @Scheduled(cron = "${app.onboarding.scheduler.cron:0 0 9 * * *}", zone = "${app.onboarding.scheduler.zone:Africa/Lagos}")
    public void processAbandonedOnboarding() {
        if (!schedulerEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(abandonmentThreshold);
        int size = Math.max(batchSize, 1);
        List<String> ids = new ArrayList<>();
        int pageNumber = 0;
        Page<PendingOnboarding> page;
        do {
            page = pendingOnboardingRepository.findDueForFollowUp(
                    FOLLOW_UP_ELIGIBLE, cutoff, PageRequest.of(pageNumber, size));
            page.forEach(record -> ids.add(record.getId()));
            pageNumber++;
        } while (page.hasNext());

        int reminded = 0;
        for (String id : ids) {
            if (followUp(id)) {
                reminded++;
            }
        }
        if (!ids.isEmpty()) {
            log.info("Abandoned onboarding follow-up finished | scanned={} | reminded={}", ids.size(), reminded);
        }
    }

    boolean followUp(String id) {
        PendingOnboarding record = markAbandoned(id);
        if (record == null || record.getStatus() == Status.ACCOUNT_CREATED || record.getStatus() == Status.REMINDER_SENT) {
            return false;
        }
        boolean customerSent = false;
        boolean internalSent = false;
        try {
            emailService.sendAbandonedOnboardingReminder(record);
            customerSent = true;
        } catch (Exception e) {
            log.error("Customer abandoned-onboarding reminder failed | id={} | email={}", record.getId(), record.getEmail(), e);
        }
        try {
            emailService.sendAbandonedOnboardingInternalAlert(record);
            internalSent = true;
        } catch (Exception e) {
            log.error("Internal abandoned-onboarding notification failed | id={} | email={}", record.getId(), record.getEmail(), e);
        }
        if (customerSent || internalSent) {
            markReminderDelivered(id, customerSent, internalSent);
        }
        return customerSent;
    }

    Optional<PendingOnboarding> startForTest(String email, String otpReference, Type type) {
        return start(email, otpReference, type, null);
    }

    @Transactional
    PendingOnboarding markAbandoned(String id) {
        PendingOnboarding record = pendingOnboardingRepository.findById(id).orElse(null);
        if (record == null || record.getStatus() == Status.ACCOUNT_CREATED) {
            return record;
        }
        if (record.getStatus() == Status.IN_PROGRESS) {
            record.setStatus(Status.PENDING_ONBOARDING);
            if (record.getExitReason() == null) {
                record.setExitReason(ExitReason.UNKNOWN);
            }
            appendEvent(record, "ONBOARDING_ABANDONED");
            pendingOnboardingRepository.save(record);
        }
        return record;
    }

    @Transactional
    void markReminderDelivered(String id, boolean customerSent, boolean internalSent) {
        PendingOnboarding record = pendingOnboardingRepository.findById(id).orElse(null);
        if (record == null || record.getStatus() == Status.ACCOUNT_CREATED) {
            return;
        }
        if (customerSent) {
            record.setCustomerReminderSent(true);
            record.setReminderCount(record.getReminderCount() + 1);
            record.setLastReminderAt(LocalDateTime.now());
            record.setStatus(Status.REMINDER_SENT);
            appendEvent(record, "REMINDER_SENT");
        }
        if (internalSent) {
            record.setInternalNotificationSent(true);
            appendEvent(record, "INTERNAL_NOTIFICATION_SENT");
        }
        pendingOnboardingRepository.save(record);
    }

    private Optional<PendingOnboarding> start(String email, String otpReference, Type type, String applicationId) {
        String normalized = normalizeEmail(email);
        if (!StringUtils.hasText(normalized) || hasCompletedAccount(normalized)) {
            return Optional.empty();
        }
        PendingOnboarding record = pendingOnboardingRepository
                .findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                        normalized, type, Status.ACCOUNT_CREATED)
                .orElseGet(PendingOnboarding::new);
        boolean created = record.getId() == null;
        record.setEmail(normalized);
        record.setOnboardingType(type);
        if (otpReference != null) {
            record.setOtpReference(otpReference);
        }
        if (StringUtils.hasText(applicationId)) {
            record.setApplicationId(applicationId);
        }
        if (created) {
            LocalDateTime now = LocalDateTime.now();
            record.setStartedAt(now);
            record.setCreatedAt(now);
            record.setLastCompletedStage(Stage.OTP_VERIFIED);
            record.setCurrentStage(type == Type.CORPORATE ? Stage.BASIC_DETAILS : Stage.BVN);
            appendEvent(record, "OTP_VERIFIED");
            appendEvent(record, "ONBOARDING_STARTED");
        } else {
            appendEvent(record, "OTP_VERIFIED");
        }
        touch(record);
        PendingOnboarding saved = pendingOnboardingRepository.save(record);
        if (type == Type.INDIVIDUAL && !StringUtils.hasText(saved.getApplicationId())) {
            saved.setApplicationId(saved.getId());
            saved = pendingOnboardingRepository.save(saved);
        }
        return Optional.of(saved);
    }

    private void run(String action, String email, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("Pending onboarding tracking failed after {} | email={}", action,
                    StringUtils.hasText(email) ? email : "-", e);
        }
    }

    private Optional<PendingOnboarding> requireActive(String email, Type type) {
        String normalized = normalizeEmail(email);
        if (!StringUtils.hasText(normalized) || hasCompletedAccount(normalized)) {
            return Optional.empty();
        }
        return pendingOnboardingRepository.findFirstByEmailIgnoreCaseAndOnboardingTypeAndStatusNotOrderByCreatedAtDesc(
                normalized, type, Status.ACCOUNT_CREATED);
    }

    private boolean hasCompletedAccount(String email) {
        return userRepository.findByEmailAddress(email).isPresent()
                || corporateClientRepository.findByEmailIgnoreCase(email).isPresent();
    }

    private void touch(PendingOnboarding record) {
        record.setLastActivityAt(LocalDateTime.now());
        if (record.getStatus() != Status.ACCOUNT_CREATED) {
            record.setStatus(Status.IN_PROGRESS);
        }
    }

    private void appendEvent(PendingOnboarding record, String event) {
        String entry = "{\"event\":\"" + event + "\",\"at\":\"" + LocalDateTime.now() + "\"}";
        String existing = record.getEventLog();
        if (!StringUtils.hasText(existing) || !existing.trim().endsWith("]")) {
            record.setEventLog("[" + entry + "]");
            return;
        }
        String trimmed = existing.trim();
        String withoutClose = trimmed.substring(0, trimmed.length() - 1).trim();
        record.setEventLog(withoutClose.endsWith("[") ? "[" + entry + "]" : withoutClose + "," + entry + "]");
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
