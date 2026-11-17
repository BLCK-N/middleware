package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.dtos.request.BusinessInformation;
import com.myrtle.middleware.dtos.request.CorporateCredentialsRequest;
import com.myrtle.middleware.dtos.request.MemartFileRequest;
import com.myrtle.middleware.dtos.response.CorporateOnboardingProgressResponse;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.exceptions.ForbiddenException;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.repository.PendingCorporateDataRepository;
import com.myrtle.middleware.repository.PendingCorporateDirectorRepository;
import com.myrtle.middleware.security.AuthenticatedAccountPrincipal;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The staged corporate onboarding steps must be reachable ONLY by the holder of the onboarding
 * token issued at KYC. An email address is not a secret, so no email-based lookup may exist.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorporateAccountServiceOnboardingAccessControlTest {

    @Mock private PendingCorporateDataRepository pendingCorporateDataRepository;
    @Mock private PendingCorporateDirectorRepository pendingCorporateDirectorRepository;

    private CorporateAccountService corporateAccountService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        corporateAccountService = new CorporateAccountService(
                null, null, null, null, responseUtil, null, null,
                new ValidatorUitls(responseUtil), null, null, null, null, null, null, null, null, null,
                pendingCorporateDataRepository, pendingCorporateDirectorRepository,
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(AuthenticatedAccountPrincipal principal) {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private AuthenticatedAccountPrincipal onboardingPrincipal() {
        return new AuthenticatedAccountPrincipal(
                "pending-id", "owner@company.com", Role.USER,
                AccountType.PENDING_CORPORATE_ONBOARDING, null, null, true);
    }

    private AuthenticatedAccountPrincipal normalUserPrincipal() {
        return new AuthenticatedAccountPrincipal(
                "some-user-id", "attacker@evil.com", Role.USER,
                AccountType.USER, 1, null, true);
    }

    @Test
    void anUnauthenticatedCallerCannotDriveAnyOnboardingStep() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> corporateAccountService.collectMemartFile(new MemartFileRequest("https://x/memart.pdf")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("onboarding token");

        verify(pendingCorporateDataRepository, never()).findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        verify(pendingCorporateDataRepository, never()).findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void aNormalLoggedInUserCannotDriveSomebodyElsesOnboarding() {
        authenticateAs(normalUserPrincipal());

        assertThatThrownBy(() -> corporateAccountService.collectMemartFile(new MemartFileRequest("https://x/memart.pdf")))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> corporateAccountService.verifyAndCollectKYB(
                BusinessInformation.builder().rcNumber("RC123456").build()))
                .isInstanceOf(ForbiddenException.class);

        assertThatThrownBy(() -> corporateAccountService.completeCorporateOnboarding())
                .isInstanceOf(ForbiddenException.class);

        // no onboarding was ever loaded on their behalf
        verify(pendingCorporateDataRepository, never())
                .findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        verify(pendingCorporateDataRepository, never())
                .findById(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void theOnboardingTokenHolderResolvesTheirOwnRecordById() {
        authenticateAs(onboardingPrincipal());

        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("owner@company.com");
        when(pendingCorporateDataRepository.findById("pending-id"))
                .thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(org.mockito.ArgumentMatchers.any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        corporateAccountService.collectMemartFile(new MemartFileRequest("https://x/memart.pdf"));

        verify(pendingCorporateDataRepository).findById("pending-id");
        assertThatMemartWasStored(pending);
    }

    @Test
    void stepResponsesReturnMaskedProgressNotTheRawStagedRecord() {
        authenticateAs(onboardingPrincipal());

        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("owner@company.com");
        pending.setCompanyName("Acme Limited");
        pending.setBvn("12345678901");

        when(pendingCorporateDataRepository.findById("pending-id"))
                .thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(org.mockito.ArgumentMatchers.any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Object data = corporateAccountService.collectCorporateCredentials(credentialsRequest()).getBody().getData();

        assertThat(data).isInstanceOf(CorporateOnboardingProgressResponse.class);

        CorporateOnboardingProgressResponse progress = (CorporateOnboardingProgressResponse) data;
        assertThat(progress.companyName()).isEqualTo("Acme Limited");
        assertThat(progress.maskedBvn()).isEqualTo("*********01");
        assertThat(progress.outstandingSteps()).contains("KYC", "KYB", "MEMART", "DIRECTORS");
        assertThat(progress.outstandingSteps()).doesNotContain("BANK_DETAILS");
    }

    private CorporateCredentialsRequest credentialsRequest() {
        return new CorporateCredentialsRequest(
                "1234", "Password123!", "DIRECTOR", "Nigerian", "2099-12-31",
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private void assertThatMemartWasStored(PendingCorporateData pending) {
        assertThat(pending.getMemartUrl()).isEqualTo("https://x/memart.pdf");
    }
}
