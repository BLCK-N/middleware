package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.dtos.request.UserLogin;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.services.authentication.RefreshTokenService;
import com.myrtle.middleware.utils.JwtUtils;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorporateAccountServiceLoginTest {

    @Mock
    private CorporateClientRepository corporateClientRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    private CorporateAccountService corporateAccountService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        corporateAccountService = new CorporateAccountService(
                null,
                corporateClientRepository,
                null,
                passwordEncoder,
                responseUtil,
                jwtUtils,
                null,
                new ValidatorUitls(responseUtil),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
        RefreshTokenService refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
        lenient().when(refreshTokenService.issueForCorporateClient(any())).thenReturn("refresh-token");
        ReflectionTestUtils.setField(corporateAccountService, "refreshTokenService", refreshTokenService);
    }

    @Test
    void corporateAccountLoginUsesCorporatePasswordAndAddsAccountMetadata() throws Exception {
        CorporateClient corporateClient = corporateClient();
        corporateClient.setRole(null);
        corporateClient.setTier(null);

        when(corporateClientRepository.findByEmailIgnoreCase("corp@example.com")).thenReturn(java.util.Optional.of(corporateClient));
        when(passwordEncoder.matches("secret", "corp-hash")).thenReturn(true);
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateToken(any(CorporateClient.class))).thenReturn("corp-token");

        ResponseEntity<ApiResponse> response = corporateAccountService.loginCorporateAccount(new UserLogin("corp@example.com", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertThat(data.get("accessToken")).isEqualTo("corp-token");
        assertThat(data.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(data.get("accountType")).isEqualTo(AccountType.CORPORATE_ACCOUNT.name());
        assertThat(data.get("role")).isEqualTo(Role.CORPORATE_ACCOUNT.name());
        assertThat(data.get("tier")).isEqualTo(3);
        assertThat(data.get("email")).isEqualTo("corp@example.com");
        verify(corporateClientRepository).save(corporateClient);
    }

    @Test
    void wrongCorporatePasswordFailsWithoutGeneratingToken() throws Exception {
        CorporateClient corporateClient = corporateClient();
        when(corporateClientRepository.findByEmailIgnoreCase("corp@example.com")).thenReturn(java.util.Optional.of(corporateClient));
        when(passwordEncoder.matches("wrong", "corp-hash")).thenReturn(false);

        ResponseEntity<ApiResponse> response = corporateAccountService.loginCorporateAccount(new UserLogin("corp@example.com", "wrong", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(jwtUtils, never()).generateToken(any(CorporateClient.class));
    }

    private CorporateClient corporateClient() {
        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId("corp-id");
        corporateClient.setEmail("corp@example.com");
        corporateClient.setPasswordHash("corp-hash");
        corporateClient.setRole(Role.CORPORATE_ACCOUNT);
        corporateClient.setTier(3);
        corporateClient.setBvn("12345678901");
        corporateClient.setGrantLoginAccess(true);
        return corporateClient;
    }
}
