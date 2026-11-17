package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code createLocalCorporateUser} is the single choke point through which every corporate login is
 * created - both the primary contact at onboarding completion and additional users attached later.
 * Its guards are therefore asserted here rather than trusted to callers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountServiceCorporateUserTest {

    private static final String EMAIL = "secretary@acme.com";
    private static final String BVN = "12345678901";
    private static final String CLIENT_ID = "mywealth-corp-id";
    private static final String INVESTMENT_ID = "corp-investment-id";

    @Mock private UserRepository userRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private EmailService emailService;
    @Mock private PersistUser persistUser;

    private AccountService accountService;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(
                null, null, null, null, emailService,
                otpRepository, persistUser, new ValidatorUitls(null), userRepository,
                null, null, null, null,
                null, null, null, null,
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
        ReflectionTestUtils.setField(accountService, "branchId", "branch-id");
    }

    private User newUser() {
        User user = new User();
        user.setFirstName("Ada");
        user.setLastName("Obi");
        user.setEmailAddress(EMAIL);
        user.setPhoneNumber("2348012345678");
        user.setPassword("Password123!");
        user.setBvn(BVN);
        return user;
    }

    private void stubVerifiedOtp() {
        UserOtp otp = new UserOtp();
        otp.setEmail(EMAIL);
        otp.markVerified();
        when(otpRepository.findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc(EMAIL))
                .thenReturn(Optional.of(otp));
    }

    /** Verified long enough ago that it no longer proves control of the inbox. */
    private void stubStaleVerifiedOtp() {
        UserOtp otp = new UserOtp();
        otp.setEmail(EMAIL);
        otp.setVerified(true);
        otp.setTimeUsed(java.time.LocalDateTime.now().minusDays(3));
        when(otpRepository.findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc(EMAIL))
                .thenReturn(Optional.of(otp));
    }

    @Test
    void aVerificationThatHasGoneStaleIsRejected() {
        stubStaleVerifiedOtp();

        assertThatThrownBy(() -> accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID))
                .isInstanceOf(FunctionException.class)
                .hasMessageContaining("verification has expired");

        verify(persistUser, never()).persistUser(any(), any(), any(), any(), any(), any());
    }

    private void stubSuccessfulPersist() {
        when(persistUser.persistUser(any(), any(), any(), any(), any(), eq(Role.CORPORATE_USER)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId("new-user-id");
                    return user;
                });
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aUserWithNoOtpRecordIsRejected() {
        when(otpRepository.findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID))
                .isInstanceOf(FunctionException.class)
                .hasMessageContaining("OTP not verified");

        verify(persistUser, never()).persistUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anUnverifiedOtpIsRejected() {
        UserOtp otp = new UserOtp();
        otp.setEmail(EMAIL);
        otp.setVerified(false);
        when(otpRepository.findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc(EMAIL)).thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID))
                .isInstanceOf(FunctionException.class)
                .hasMessageContaining("OTP not verified");

        verify(persistUser, never()).persistUser(any(), any(), any(), any(), any(), any());
    }

    @Test
    void anExistingEmailIsRejectedBeforeTheOtpCheck() {
        // findByEmailAddress is a default method delegating to the IgnoreCase variant; Mockito mocks
        // it directly, so it is the one that must be stubbed.
        when(userRepository.findByEmailAddress(EMAIL)).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID))
                .isInstanceOf(FunctionException.class)
                .hasMessageContaining("Account already exists");
    }

    @Test
    void aBvnAlreadyOnAnotherAccountIsRejected() {
        when(userRepository.findByBvn(BVN)).thenReturn(Optional.of(new User()));
        stubVerifiedOtp();

        assertThatThrownBy(() -> accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID))
                .isInstanceOf(FunctionException.class)
                .hasMessageContaining("BVN already exists");
    }

    @Test
    void aVerifiedUserIsCreatedAtTierThreeAgainstTheCorporateAccount() {
        stubVerifiedOtp();
        stubSuccessfulPersist();

        User created = accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID);

        assertThat(created.getTier()).isEqualTo("3");

        verify(persistUser).persistUser(
                any(User.class), any(), eq(CLIENT_ID), eq(INVESTMENT_ID), eq("branch-id"), eq(Role.CORPORATE_USER));
    }

    @Test
    void aFailedWelcomeEmailDoesNotFailAccountCreation() {
        stubVerifiedOtp();
        stubSuccessfulPersist();
        org.mockito.Mockito.doThrow(new RuntimeException("SES down"))
                .when(emailService).sendWelcomeEmail(any(User.class));

        User created = accountService.createLocalCorporateUser(
                newUser(), List.of(), CLIENT_ID, INVESTMENT_ID);

        assertThat(created.getId()).isEqualTo("new-user-id");
    }
}
