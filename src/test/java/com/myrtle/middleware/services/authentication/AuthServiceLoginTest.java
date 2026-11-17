package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.DeviceDetailsRequest;
import com.myrtle.middleware.dtos.request.RefreshTokenRequest;
import com.myrtle.middleware.dtos.request.SendOtp;
import com.myrtle.middleware.dtos.request.UserLogin;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.DeviceDetailsResponse;
import com.myrtle.middleware.dtos.response.IssuedTokens;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.ActivationStatus;
import com.myrtle.middleware.enums.DevicePlatform;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.exceptions.InvalidRefreshTokenException;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.corporate.CorporateAccountService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    @Mock
    private ExternalAPIs externalAPIs;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CorporateClientRepository corporateClientRepository;

    @Mock
    private OtpRepository otpRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private CorporateAccountService corporateAccountService;

    @Mock
    private DeviceDetailsService deviceDetailsService;

    @Mock
    private BiometricAuthService biometricAuthService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        authService = new AuthService(
                responseUtil,
                null,
                externalAPIs,
                userRepository,
                corporateClientRepository,
                null,
                otpRepository,
                passwordEncoder,
                jwtUtils,
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
        ReflectionTestUtils.setField(authService, "corporateAccountService", corporateAccountService);
        ReflectionTestUtils.setField(authService, "deviceDetailsService", deviceDetailsService);
        ReflectionTestUtils.setField(authService, "refreshTokenService", refreshTokenService);
        ReflectionTestUtils.setField(authService, "biometricAuthService", biometricAuthService);
        lenient().when(jwtUtils.accessTokenTtlSeconds()).thenReturn(900L);
        lenient().when(refreshTokenService.issueForUser(any())).thenReturn("refresh-token");
        lenient().when(biometricAuthService.validateBioToken(any(), any(), any(), any())).thenAnswer(inv -> {
            String email = inv.getArgument(0);
            if ("user@example.com".equalsIgnoreCase(email)) {
                return normalUser();
            }
            return null;
        });
    }

    @Test
    void normalUserLoginKeepsExistingResponseShapeAndDoesNotCheckCorporatePassword() throws Exception {
        User user = normalUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "user-hash")).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("user-token");

        ResponseEntity<ApiResponse> response = authService.login(new UserLogin("USER@example.com", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertThat(data.get("user")).isSameAs(user);
        assertThat(data.get("accessToken")).isEqualTo("user-token");
        assertThat(data.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(data.containsKey("isBvnVerified")).isTrue();
        assertThat(data.containsKey("isNinVerified")).isTrue();
        assertThat(data.containsKey("kycCompleted")).isTrue();
        assertThat(data.containsKey("myWealthData")).isTrue();
        assertThat(data.containsKey("accountType")).isFalse();
        verify(corporateClientRepository, never()).findByEmailIgnoreCase(any());
        verify(deviceDetailsService).saveFromLogin(user, null);
    }

    @Test
    void normalUserLoginSavesOptionalDeviceDetailsAgainstAuthenticatedUser() throws Exception {
        User user = normalUser();
        DeviceDetailsRequest device = new DeviceDetailsRequest(
                "ios-install-abc-123",
                "David's iPhone",
                "IOS",
                "1.2.0"
        );
        DeviceDetailsResponse savedDevice = new DeviceDetailsResponse(
                "device-uuid",
                "user-id",
                "ios-install-abc-123",
                "David's iPhone",
                DevicePlatform.IOS,
                "1.2.0",
                ActivationStatus.ACTIVE,
                null,
                null,
                null
        );
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "user-hash")).thenReturn(true);
        when(jwtUtils.generateToken(user)).thenReturn("user-token");
        when(deviceDetailsService.saveFromLogin(user, device)).thenReturn(savedDevice);

        ResponseEntity<ApiResponse> response = authService.login(
                new UserLogin("USER@example.com", "secret", "ip", device)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertThat(data.get("accessToken")).isEqualTo("user-token");
        assertThat(data.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(data.get("device")).isEqualTo(savedDevice);
        verify(deviceDetailsService).saveFromLogin(user, device);
    }

    @Test
    void biometricLoginAcceptsBioTokenFromValidDeviceAndReturnsTokenBundle() throws Exception {
        User user = normalUser();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(jwtUtils.generateToken(user)).thenReturn("user-token");

        ResponseEntity<ApiResponse> response = authService.login(
                new UserLogin("USER@example.com", null, "ip", null, "USER@example.com", "valid-bio-token"),
                "device-123",
                "ANDROID"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertThat(data.get("accessToken")).isEqualTo("user-token");
        assertThat(data.get("refreshToken")).isEqualTo("refresh-token");
        assertThat(data.containsKey("access_token")).isFalse();
        assertThat(data.containsKey("refresh_token")).isFalse();
        assertThat(data.get("token_type")).isEqualTo("Bearer");
        assertThat(data.get("expires_in")).isEqualTo(15 * 60L);
    }

    @Test
    void regularLoginAuthenticatesCorporateAccountWhenEmailExistsInCorporateClientRepo() throws Exception {
        CorporateClient corporateClient = corporateClient();
        when(userRepository.findByEmailAddress("corp@example.com")).thenReturn(Optional.empty());
        when(corporateClientRepository.findByEmailIgnoreCase("corp@example.com")).thenReturn(Optional.of(corporateClient));

        Map<String, Object> corporateLoginData = Map.of(
                "accessToken", "corp-token",
                "accountType", AccountType.CORPORATE_ACCOUNT.name()
        );
        ApiResponse corporateLoginBody = new ApiResponse(true, "Login Successful", corporateLoginData);
        when(corporateAccountService.authenticateCorporateClient(any(UserLogin.class), any(CorporateClient.class)))
                .thenReturn(ResponseEntity.ok(corporateLoginBody));

        ResponseEntity<ApiResponse> response = authService.login(new UserLogin("corp@example.com", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertThat(data.get("accessToken")).isEqualTo("corp-token");
        assertThat(data.get("accountType")).isEqualTo(AccountType.CORPORATE_ACCOUNT.name());
    }

    @Test
    void regularLoginReturnsNotFoundWhenEmailMissingFromBothRepos() throws Exception {
        when(userRepository.findByEmailAddress("missing@example.com")).thenReturn(Optional.empty());
        when(corporateClientRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse> response = authService.login(new UserLogin("missing@example.com", "secret", "ip"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void refreshSessionReturnsANewAccessTokenAndRefreshToken() {
        when(refreshTokenService.rotate("raw-refresh")).thenReturn(new IssuedTokens("new-access", "new-refresh"));

        ResponseEntity<ApiResponse> response = authService.refreshSession(new RefreshTokenRequest("raw-refresh"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        IssuedTokens tokens = (IssuedTokens) response.getBody().getData();
        assertThat(tokens.accessToken()).isEqualTo("new-access");
        assertThat(tokens.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refreshSessionRejectsABlankOrReusedToken() {
        ResponseEntity<ApiResponse> blank = authService.refreshSession(new RefreshTokenRequest("  "));
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        when(refreshTokenService.rotate("used")).thenThrow(new InvalidRefreshTokenException("Refresh token is invalid or has expired."));
        ResponseEntity<ApiResponse> reused = authService.refreshSession(new RefreshTokenRequest("used"));
        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reused.getBody().getMessage()).isEqualTo("Refresh token is invalid or has expired.");
    }

    @Test
    void sendOtpExistenceCheckRejectsExistingCorporateAccount() {
        when(externalAPIs.checkIfEmailExists("corp@example.com")).thenReturn(false);
        when(userRepository.findByEmailAddress("corp@example.com")).thenReturn(Optional.empty());
        when(corporateClientRepository.findByEmailIgnoreCase("corp@example.com")).thenReturn(Optional.of(corporateClient()));

        ResponseEntity<ApiResponse> response = authService.sendOtp(new SendOtp("corp@example.com", true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(otpRepository, never()).save(any());
    }

    private User normalUser() {
        User user = new User();
        user.setId("user-id");
        user.setEmailAddress("user@example.com");
        user.setPassword("user-hash");
        user.setRole(Role.USER);
        user.setTier("2");
        user.setBvn("12345678901");
        user.setNin("12345678901");
        return user;
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
