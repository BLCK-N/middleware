package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.dtos.bvn.QoreIdBVNResponse;
import com.myrtle.middleware.dtos.request.CorporateLookupRequest;
import com.myrtle.middleware.dtos.request.CreateCorporateUserRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.CorporateLookupResponse;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.repository.BvnCacheRepo;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.authentication.AccountService;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.authentication.RefreshTokenService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.BvnCheckService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers attaching additional logins (CEO, company secretary, board chairman...) to an existing
 * corporate account via the RC number + registered BVN lookup.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CorporateAccountServiceUserAttachTest {

    private static final String RC_NUMBER = "RC123456";
    private static final String REGISTERED_BVN = "12345678901";
    private static final String NEW_USER_BVN = "10987654321";

    @Mock private ExternalAPIs externalAPIs;
    @Mock private UserRepository userRepository;
    @Mock private CorporateClientRepository corporateClientRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private BvnCheckService bvnCheckService;
    @Mock private BvnCacheRepo bvnCacheRepo;
    @Mock private AccountService accountService;
    @Mock private AuthService authService;

    private CorporateAccountService corporateAccountService;
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();

        jwtUtils = new JwtUtils(userRepository);
        ReflectionTestUtils.setField(jwtUtils, "secret",
                "test-secret-key-that-is-long-enough-for-hmac-sha256");

        corporateAccountService = new CorporateAccountService(
                externalAPIs,
                corporateClientRepository,
                null,
                passwordEncoder,
                responseUtil,
                jwtUtils,
                null,
                new ValidatorUitls(responseUtil),
                accountService,
                null,
                null,
                userRepository,
                otpRepository,
                null,
                bvnCheckService,
                bvnCacheRepo,
                authService,
                null,
                null,
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
        RefreshTokenService refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
        lenient().when(refreshTokenService.issueForUser(any())).thenReturn("refresh-token");
        ReflectionTestUtils.setField(corporateAccountService, "refreshTokenService", refreshTokenService);
    }

    private CorporateClient corporateClient() {
        CorporateClient client = new CorporateClient();
        client.setId("corp-id");
        client.setRcNumber(RC_NUMBER);
        client.setBvn(REGISTERED_BVN);
        client.setCompanyName("Acme Limited");
        client.setRefCode("REF123");
        client.setEmail("accounts@acme.com");
        client.setExternalClientId("mywealth-corp-id");
        client.setInvestmentId("corp-investment-id");
        return client;
    }

    @Test
    void lookupWithWrongBvnIsRejectedAndLeaksNothingAboutTheCompany() {
        when(corporateClientRepository.findByRcNumberIgnoreCase(RC_NUMBER))
                .thenReturn(Optional.of(corporateClient()));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.lookupCorporateAccount(new CorporateLookupRequest(RC_NUMBER, "99999999999"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isNull();
        assertThat(response.getBody().getMessage()).doesNotContain("Acme");
    }

    @Test
    void lookupWithUnknownRcNumberReturnsTheSameResponseAsAWrongBvn() {
        when(corporateClientRepository.findByRcNumberIgnoreCase("RC000000")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse> unknownRc =
                corporateAccountService.lookupCorporateAccount(new CorporateLookupRequest("RC000000", REGISTERED_BVN));

        when(corporateClientRepository.findByRcNumberIgnoreCase(RC_NUMBER))
                .thenReturn(Optional.of(corporateClient()));
        ResponseEntity<ApiResponse> wrongBvn =
                corporateAccountService.lookupCorporateAccount(new CorporateLookupRequest(RC_NUMBER, "99999999999"));

        assertThat(unknownRc.getStatusCode()).isEqualTo(wrongBvn.getStatusCode());
        assertThat(unknownRc.getBody().getMessage()).isEqualTo(wrongBvn.getBody().getMessage());
    }

    @Test
    void successfulLookupReturnsMaskedEmailAndAUsableAttachToken() {
        when(corporateClientRepository.findByRcNumberIgnoreCase(RC_NUMBER))
                .thenReturn(Optional.of(corporateClient()));
        when(userRepository.countActiveByCorporateClientId("corp-id")).thenReturn(2L);

        ResponseEntity<ApiResponse> response =
                corporateAccountService.lookupCorporateAccount(new CorporateLookupRequest(RC_NUMBER, REGISTERED_BVN));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CorporateLookupResponse data = (CorporateLookupResponse) response.getBody().getData();

        assertThat(data.companyName()).isEqualTo("Acme Limited");
        assertThat(data.existingUsers()).isEqualTo(2);
        assertThat(data.maxUsers()).isEqualTo(CorporateAccountService.MAX_CORPORATE_USERS);
        assertThat(data.maskedEmail()).isEqualTo("a***s@acme.com");
        assertThat(jwtUtils.extractCorporateAttachClientId(data.lookupToken())).isEqualTo("corp-id");
    }

    @Test
    void createCorporateUserIsRejectedWithoutAValidAttachToken() {
        ResponseEntity<ApiResponse> response =
                corporateAccountService.createCorporateUserMain(request("not-a-real-token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(accountService, never()).createLocalCorporateUser(any(), any(), any(), any());
    }

    @Test
    void aNormalLoginTokenCannotBeUsedAsAnAttachToken() {
        User someUser = new User();
        someUser.setId("user-id");
        someUser.setEmailAddress("someone@example.com");
        someUser.setTier("1");

        String loginToken = jwtUtils.generateToken(someUser);

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateUserMain(request(loginToken));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(accountService, never()).createLocalCorporateUser(any(), any(), any(), any());
    }

    @Test
    void createCorporateUserIsRejectedOnceTheCompanyHitsTheUserCap() {
        CorporateClient client = corporateClient();
        when(corporateClientRepository.findById("corp-id")).thenReturn(Optional.of(client));
        when(userRepository.countActiveByCorporateClientId("corp-id"))
                .thenReturn((long) CorporateAccountService.MAX_CORPORATE_USERS);

        ResponseEntity<ApiResponse> response =
                corporateAccountService.createCorporateUserMain(request(jwtUtils.generateCorporateAttachToken(client)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("maximum of 5");
        verify(accountService, never()).createLocalCorporateUser(any(), any(), any(), any());
    }

    @Test
    void anOtpIssuedToADifferentEmailCannotAuthoriseThisUser() {
        CorporateClient client = corporateClient();
        when(corporateClientRepository.findById("corp-id")).thenReturn(Optional.of(client));
        when(userRepository.countActiveByCorporateClientId("corp-id")).thenReturn(1L);

        UserOtp otpIssuedToSomeoneElse = new UserOtp();
        otpIssuedToSomeoneElse.setEmail("attacker@evil.com");
        otpIssuedToSomeoneElse.setOtp("hashed-otp");
        otpIssuedToSomeoneElse.setOtpReference("ref-123");
        when(otpRepository.findByOtpReference("ref-123")).thenReturn(Optional.of(otpIssuedToSomeoneElse));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.createCorporateUserMain(request(jwtUtils.generateCorporateAttachToken(client)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("does not match this email");
        verify(accountService, never()).createLocalCorporateUser(any(), any(), any(), any());
    }

    @Test
    void successfulAttachPointsTheUserAtTheCorporateClientAndCreatesNoMyWealthClient() {
        CorporateClient client = corporateClient();
        when(corporateClientRepository.findById("corp-id")).thenReturn(Optional.of(client));
        when(userRepository.countActiveByCorporateClientId("corp-id")).thenReturn(1L);

        UserOtp userOtp = new UserOtp();
        userOtp.setEmail("secretary@acme.com");
        userOtp.setOtp("hashed-otp");
        userOtp.setOtpReference("ref-123");
        when(otpRepository.findByOtpReference("ref-123")).thenReturn(Optional.of(userOtp));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(passwordEncoder.encode("1234")).thenReturn("hashed-pin");

        when(externalAPIs.verifyBvnPremium(any())).thenReturn(
                QoreIdBVNResponse.builder()
                        .bvn(QoreIdBVNResponse.Bvn.builder().middlename("Ada").build())
                        .build());

        User persisted = new User();
        persisted.setId("new-user-id");
        persisted.setEmailAddress("secretary@acme.com");
        persisted.setTier("3");
        when(accountService.createLocalCorporateUser(any(), any(), any(), any())).thenReturn(persisted);

        ResponseEntity<ApiResponse> response =
                corporateAccountService.createCorporateUserMain(request(jwtUtils.generateCorporateAttachToken(client)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> investmentIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountService).createLocalCorporateUser(
                userCaptor.capture(), any(), clientIdCaptor.capture(), investmentIdCaptor.capture());

        assertThat(clientIdCaptor.getValue()).isEqualTo("mywealth-corp-id");
        assertThat(investmentIdCaptor.getValue()).isEqualTo("corp-investment-id");

        User handedOff = userCaptor.getValue();
        assertThat(handedOff.getEmailAddress()).isEqualTo("secretary@acme.com");
        assertThat(handedOff.getPassword()).isEqualTo("Password123!"); // raw - persistUser hashes it
        assertThat(handedOff.getPin()).isEqualTo("hashed-pin");
        assertThat(handedOff.isUsePin()).isTrue();
        assertThat(handedOff.getCorporateClient()).isSameAs(client);

        verify(externalAPIs, never()).createIndividualClient(any(), any(), anyBoolean(), any());
        verify(externalAPIs, never()).createCorporateClient(any());
    }

    @Test
    void anRcNumberThatDisagreesWithTheTokenIsRejected() {
        CorporateClient client = corporateClient();
        when(corporateClientRepository.findById("corp-id")).thenReturn(Optional.of(client));

        CreateCorporateUserRequest mismatched = new CreateCorporateUserRequest(
                jwtUtils.generateCorporateAttachToken(client),
                null,
                "RC999999",
                "Ada", "Obi", null,
                "secretary@acme.com", "2348012345678",
                "Password123!", "1234",
                "123456", "ref-123",
                NEW_USER_BVN, "1 Acme Way", "female",
                false, null, List.of()
        );

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateUserMain(mismatched);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("RC number does not match");
        verify(accountService, never()).createLocalCorporateUser(any(), any(), any(), any());
    }

    private CreateCorporateUserRequest request(String lookupToken) {
        return new CreateCorporateUserRequest(
                lookupToken,
                null,
                null,
                "Ada", "Obi", null,
                "secretary@acme.com", "2348012345678",
                "Password123!", "1234",
                "123456", "ref-123",
                NEW_USER_BVN, "1 Acme Way", "female",
                false, null, List.of()
        );
    }
}
