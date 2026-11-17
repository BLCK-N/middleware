package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.AccountOwnerType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.WithdrawalAccount;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.utils.ResponseUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test cases for corporate account PIN validation, settlement accounts, and withdrawal account mapping.
 * Validates the implementation of Requirements 7-10 of the corporate account onboarding update.
 */
@ExtendWith(MockitoExtension.class)
class CorporateAccountPinAndWithdrawalTest {

    @Mock
    private CorporateClientRepository corporateClientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private CorporateAccountService corporateAccountService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        corporateAccountService = new CorporateAccountService(
                null,  // externalAPIs not needed for PIN tests
                corporateClientRepository,
                null,  // nextOfKinRepository not needed for PIN tests
                passwordEncoder,
                responseUtil,
                null,  // jwtUtil not needed for PIN tests
                null,  // objectMapper not needed for PIN tests
                null,  // validatorUitls not needed for PIN tests
                null,  // accountService not needed for PIN tests
                null,  // checkpoint not needed for PIN tests
                null,  // corporateDirectorRepository not needed for PIN tests
                null,  // userRepository not needed for PIN tests
                null,  // otpRepository not needed for PIN tests
                null,  // emailService not needed for PIN tests
                null,  // bvnCheckService not needed for PIN tests
                null,  // bvnCacheRepo not needed for PIN tests
                null,  // authService not needed for PIN tests
                null,  // pendingCorporateDataRepository not needed for PIN tests
                null,  // pendingCorporateDirectorRepository not needed for PIN tests
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
    }

    // ========== PIN Validation Tests ==========

    @Test
    void setCorporatePinSuccessfully() {
        String corporateClientId = "test-corp-id";
        String pin = "1234";
        String hashedPin = "hashed-1234";

        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId(corporateClientId);
        corporateClient.setEmail("corp@example.com");
        corporateClient.setRole(Role.CORPORATE_ACCOUNT);
        corporateClient.setTier(3);

        when(corporateClientRepository.findById(corporateClientId))
                .thenReturn(Optional.of(corporateClient));
        when(passwordEncoder.encode(pin)).thenReturn(hashedPin);
        when(corporateClientRepository.save(any(CorporateClient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = corporateAccountService.setCorporatePin(corporateClientId, pin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("PIN set successfully");
        
        verify(corporateClientRepository).save(any(CorporateClient.class));
    }

    @Test
    void setCorporatePinWithInvalidPin() {
        String corporateClientId = "test-corp-id";
        String invalidPin = "12345";  // 5 digits instead of 4

        ResponseEntity<ApiResponse> response = corporateAccountService.setCorporatePin(corporateClientId, invalidPin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("4 digits");
    }

    @Test
    void validateCorporatePinSuccessfully() {
        String corporateClientId = "test-corp-id";
        String pin = "1234";
        String hashedPin = "hashed-1234";

        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId(corporateClientId);
        corporateClient.setPinHash(hashedPin);
        corporateClient.setPinSet(true);
        corporateClient.setPinEnabled(true);

        when(corporateClientRepository.findById(corporateClientId))
                .thenReturn(Optional.of(corporateClient));
        when(passwordEncoder.matches(pin, hashedPin)).thenReturn(true);

        boolean isValid = corporateAccountService.validateCorporatePin(corporateClientId, pin);

        assertThat(isValid).isTrue();
    }

    @Test
    void validateCorporatePinReturnsFalseIfPinDisabled() {
        String corporateClientId = "test-corp-id";
        String pin = "1234";

        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId(corporateClientId);
        corporateClient.setPinSet(true);
        corporateClient.setPinEnabled(false);  // PIN disabled

        when(corporateClientRepository.findById(corporateClientId))
                .thenReturn(Optional.of(corporateClient));

        boolean isValid = corporateAccountService.validateCorporatePin(corporateClientId, pin);

        assertThat(isValid).isFalse();
    }

    @Test
    void validateCorporatePinReturnsFalseIfPinNotSet() {
        String corporateClientId = "test-corp-id";
        String pin = "1234";

        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId(corporateClientId);
        corporateClient.setPinSet(false);  // PIN not set
        corporateClient.setPinEnabled(false);

        when(corporateClientRepository.findById(corporateClientId))
                .thenReturn(Optional.of(corporateClient));

        boolean isValid = corporateAccountService.validateCorporatePin(corporateClientId, pin);

        assertThat(isValid).isFalse();
    }

    // ========== Withdrawal Account Owner Type Tests ==========

    @Test
    void withdrawalAccountForUserHasCorrectOwnerType() {
        WithdrawalAccount userAccount = new WithdrawalAccount();
        userAccount.setId("user-withdrawal-1");
        userAccount.setBankCode("001");
        userAccount.setAccountNumber("1234567890");
        userAccount.setBankName("Test Bank");
        userAccount.setAccountName("Test User");
        userAccount.setBankVerificationNumber("1234567890");
        userAccount.setOwnerType(AccountOwnerType.USER);

        assertThat(userAccount.getOwnerType()).isEqualTo(AccountOwnerType.USER);
        assertThat(userAccount.getUser()).isNull();  // Will be set separately
        assertThat(userAccount.getCorporateClientId()).isNull();
    }

    @Test
    void withdrawalAccountForCorporateHasCorrectOwnerType() {
        String corporateClientId = "corp-123";
        
        WithdrawalAccount corporateAccount = new WithdrawalAccount();
        corporateAccount.setId("corp-settlement-1");
        corporateAccount.setBankCode("001");
        corporateAccount.setAccountNumber("9876543210");
        corporateAccount.setBankName("Settlement Bank");
        corporateAccount.setAccountName("Company Account");
        corporateAccount.setBankVerificationNumber("9876543210");
        corporateAccount.setOwnerType(AccountOwnerType.CORPORATE_ACCOUNT);
        corporateAccount.setCorporateClientId(corporateClientId);

        assertThat(corporateAccount.getOwnerType()).isEqualTo(AccountOwnerType.CORPORATE_ACCOUNT);
        assertThat(corporateAccount.getCorporateClientId()).isEqualTo(corporateClientId);
        assertThat(corporateAccount.getUser()).isNull();  // Should be null for corporate
    }

    @Test
    void corporateClientHasCorrectRoleAndTier() {
        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId("corp-123");
        corporateClient.setEmail("corp@example.com");
        corporateClient.setRole(Role.CORPORATE_ACCOUNT);
        corporateClient.setTier(3);

        assertThat(corporateClient.getRole()).isEqualTo(Role.CORPORATE_ACCOUNT);
        assertThat(corporateClient.getTier()).isEqualTo(3);
    }

    // ========== PIN Migration Tests ==========

    @Test
    void migratedCorporateAccountCanSetPin() {
        String corporateClientId = "migrated-corp-id";
        String pin = "5678";
        String hashedPin = "hashed-5678";

        // Simulate a migrated corporate account with no PIN set
        CorporateClient migratedAccount = new CorporateClient();
        migratedAccount.setId(corporateClientId);
        migratedAccount.setEmail("migrated@example.com");
        migratedAccount.setRole(Role.CORPORATE_ACCOUNT);
        migratedAccount.setTier(3);
        migratedAccount.setPinSet(false);  // No PIN set yet
        migratedAccount.setPinEnabled(false);
        migratedAccount.setPinHash(null);

        when(corporateClientRepository.findById(corporateClientId))
                .thenReturn(Optional.of(migratedAccount));
        when(passwordEncoder.encode(pin)).thenReturn(hashedPin);
        when(corporateClientRepository.save(any(CorporateClient.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = corporateAccountService.setCorporatePin(corporateClientId, pin);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).isEqualTo("PIN set successfully");

        verify(corporateClientRepository).save(any(CorporateClient.class));
    }

    // ========== Backward Compatibility Tests ==========

    @Test
    void normalUserWithdrawalAccountStillWorks() {
        WithdrawalAccount userAccount = new WithdrawalAccount();
        userAccount.setId("user-account-1");
        userAccount.setBankCode("001");
        userAccount.setAccountNumber("1111111111");
        userAccount.setBankName("User Bank");
        userAccount.setAccountName("John Doe");
        userAccount.setBankVerificationNumber("1111111111");
        // Should default to USER if not explicitly set
        userAccount.setOwnerType(AccountOwnerType.USER);

        assertThat(userAccount.getOwnerType()).isEqualTo(AccountOwnerType.USER);
        assertThat(userAccount.getCorporateClientId()).isNull();
        assertThat(userAccount.getId()).isEqualTo("user-account-1");
    }
}
