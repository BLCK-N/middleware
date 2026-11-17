package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.VerifyDto;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePendingOnboardingTest {

    @Mock private UserRepository userRepository;
    @Mock private CorporateClientRepository corporateClientRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private PendingOnboardingService pendingOnboardingService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        authService = new AuthService(
                responseUtil,
                null,
                null,
                userRepository,
                corporateClientRepository,
                null,
                otpRepository,
                passwordEncoder,
                null,
                null,
                null,
                null,
                new ValidatorUitls(responseUtil),
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(authService, "pendingOnboardingService", pendingOnboardingService);
    }

    @Test
    void successfulOtpVerificationStartsPendingOnboarding() {
        UserOtp otp = new UserOtp();
        otp.setEmail("alex@example.com");
        otp.setOtp("hashed");
        otp.setOtpReference("otp-ref-1");
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref-1", "alex@example.com"))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("123456", "hashed")).thenReturn(true);

        ResponseEntity<ApiResponse> response = authService.verifyOtp(
                new VerifyDto("Alex@example.com", "123456", "otp-ref-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pendingOnboardingService).startIndividual("alex@example.com", "otp-ref-1");
    }

    @Test
    void invalidOtpDoesNotStartPendingOnboarding() {
        UserOtp otp = new UserOtp();
        otp.setEmail("alex@example.com");
        otp.setOtp("hashed");
        otp.setOtpReference("otp-ref-1");
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref-1", "alex@example.com"))
                .thenReturn(Optional.of(otp));
        when(passwordEncoder.matches("000000", "hashed")).thenReturn(false);

        ResponseEntity<ApiResponse> response = authService.verifyOtp(
                new VerifyDto("alex@example.com", "000000", "otp-ref-1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(pendingOnboardingService, never()).startIndividual("alex@example.com", "otp-ref-1");
    }
}
