package com.myrtle.middleware.services.corporate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.dtos.request.CreateCorporateClientRequest;
import com.myrtle.middleware.dtos.request.InvestmentAccountRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.InvestmentAccountResponse;
import com.myrtle.middleware.dtos.response.MyWealthClientResponse;
import com.myrtle.middleware.dtos.response.TokenResponse;
import com.myrtle.middleware.dtos.response.UsernameAvailabilityResponse;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.CorporateNextOfKin;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.CorporateDirectorRepository;
import com.myrtle.middleware.repository.CorporateNextOfKinRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.authentication.AccountService;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.authentication.RefreshTokenService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CorporateAccountServiceTest {

    @Mock
    private ExternalAPIs externalAPIs;

    @Mock
    private CorporateClientRepository corporateClientRepository;

    @Mock
    private CorporateNextOfKinRepository corporateNextOfKinRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthService authService;

    @Mock
    private AccountService accountService;

    @Mock
    private CorporateOnboardingCheckpoint checkpoint;

    @Mock
    private CorporateDirectorRepository corporateDirectorRepository;

    private CorporateAccountService corporateAccountService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        corporateAccountService = new CorporateAccountService(
                externalAPIs,
                corporateClientRepository,
                corporateNextOfKinRepository,
                passwordEncoder,
                responseUtil,
                jwtUtils,
                new ObjectMapper(),
                new ValidatorUitls(responseUtil),
                accountService,
                checkpoint,
                corporateDirectorRepository,
                userRepository,
                null,  // otpRepository not needed for these tests
                null,  // emailService not needed for these tests
                null,  // bvnCheckService not needed for these tests
                null,  // bvnCacheRepo not needed for these tests
                authService,
                null,  // pendingCorporateDataRepository not needed for these tests
                null,  // pendingCorporateDirectorRepository not needed for these tests
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
        ReflectionTestUtils.setField(corporateAccountService, "clientGroupId", "configured-group-id");
        ReflectionTestUtils.setField(corporateAccountService, "branchId", "configured-branch-id");

        // Username availability is now a precondition for every path that reaches MyWealth; lenient
        // because the validation-failure tests never get that far.
        lenient().when(externalAPIs.isContactUsernameAvailable(any()))
                .thenReturn(availability(true));
        RefreshTokenService refreshTokenService = org.mockito.Mockito.mock(RefreshTokenService.class);
        lenient().when(refreshTokenService.issueForUser(any())).thenReturn("refresh-token");
        ReflectionTestUtils.setField(corporateAccountService, "refreshTokenService", refreshTokenService);
    }

    private UsernameAvailabilityResponse availability(Boolean available) {
        UsernameAvailabilityResponse response = new UsernameAvailabilityResponse();
        response.setAvailable(available);
        return response;
    }

    /** A staged record with no checkpoint - a first attempt. */
    private PendingCorporateData pending() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("corporateclient@example.com");
        return pending;
    }

    /** A staged record carrying checkpoints from an attempt whose local work rolled back. */
    private PendingCorporateData pendingWithCheckpoint(String clientId, String investmentId) {
        PendingCorporateData pending = pending();
        pending.setExternalClientId(clientId);
        pending.setExternalInvestmentId(investmentId);
        return pending;
    }

    /**
     * Lenient throughout: the resume tests deliberately skip some of these steps, so which stubs get
     * used varies by scenario.
     */
    private void stubLocalPersistence() {
        User createdUser = new User();
        createdUser.setClientId("mywealth-client-id");

        InvestmentAccountResponse investmentAccountResponse = new InvestmentAccountResponse();
        investmentAccountResponse.setId("investment-account-id");

        lenient().when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        lenient().when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        lenient().when(accountService.createLocalCorporateUser(any(User.class), any(), any(), any()))
                .thenReturn(createdUser);
        lenient().when(externalAPIs.createAnInvestmentAccount(any())).thenReturn(investmentAccountResponse);
        lenient().when(jwtUtils.generateToken(any(User.class))).thenReturn("corp-user-token");
    }

    @Test
    void theStoredContactsPayloadCarriesNoCredentials() {
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        stubLocalPersistence();

        corporateAccountService.createCorporateAccount(sampleRequest("MULTI_MEMBER_LLC"), pending());

        ArgumentCaptor<CorporateClient> captor = ArgumentCaptor.forClass(CorporateClient.class);
        verify(corporateClientRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        String contactsPayload = captor.getAllValues().getFirst().getContactsPayload();

        assertThat(contactsPayload).isNotNull();
        // the request's real password, which must never reach this column
        assertThat(contactsPayload).doesNotContain("Password123!");
        // the field is gone entirely, not merely masked
        assertThat(contactsPayload).doesNotContain("password");
        // non-sensitive contact data is still recorded
        assertThat(contactsPayload).contains("david@example.com");
    }

    @Test
    void theCorporateClientIdIsCheckpointedAsSoonAsMyWealthReturnsIt() {
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        stubLocalPersistence();
        PendingCorporateData pending = pending();

        corporateAccountService.createCorporateAccount(sampleRequest("MULTI_MEMBER_LLC"), pending);

        verify(checkpoint).recordExternalClientId("pending-id", "mywealth-client-id");
        // the caller's in-memory copy must match, or flushing it stale would null the checkpoint
        assertThat(pending.getExternalClientId()).isEqualTo("mywealth-client-id");
    }

    @Test
    void aRetryReusesTheCheckpointedClientInsteadOfCreatingASecondOne() {
        stubLocalPersistence();

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(
                sampleRequest("MULTI_MEMBER_LLC"),
                pendingWithCheckpoint("existing-client-id", null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(externalAPIs, never()).createCorporateClient(any());
        // and the username check is skipped - we already own that username, so it reads as taken
        verify(externalAPIs, never()).isContactUsernameAvailable(any());
    }

    @Test
    void aRetryReusesTheCheckpointedInvestmentAccount() {
        stubLocalPersistence();

        corporateAccountService.createCorporateAccount(
                sampleRequest("MULTI_MEMBER_LLC"),
                pendingWithCheckpoint("existing-client-id", "existing-investment-id"));

        verify(externalAPIs, never()).createAnInvestmentAccount(any());

        ArgumentCaptor<CorporateClient> captor = ArgumentCaptor.forClass(CorporateClient.class);
        verify(corporateClientRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues().getLast().getInvestmentId()).isEqualTo("existing-investment-id");
    }

    @Test
    void aRetryStillCreatesTheInvestmentAccountWhenOnlyTheClientWasCheckpointed() {
        stubLocalPersistence();

        corporateAccountService.createCorporateAccount(
                sampleRequest("MULTI_MEMBER_LLC"),
                pendingWithCheckpoint("existing-client-id", null));

        verify(externalAPIs).createAnInvestmentAccount(any());
        verify(checkpoint).recordInvestmentId(eq("pending-id"), any());
    }

    @Test
    @Disabled("MyWealth username-availability check is commented out in "
            + "CorporateAccountService.validateCorporateRequest pending a fix on MyWealth's side - "
            + "re-enable this test when that call is restored.")
    void aUsernameAlreadyTakenOnMyWealthIsRejectedBeforeCreating() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.isContactUsernameAvailable(any())).thenReturn(availability(false));

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("already exists");
        verify(externalAPIs, never()).createCorporateClient(any());
    }

    @Test
    @Disabled("MyWealth username-availability check is commented out in "
            + "CorporateAccountService.validateCorporateRequest pending a fix on MyWealth's side - "
            + "re-enable this test when that call is restored.")
    void anUnconfirmableUsernameBlocksCreationRatherThanAssumingItIsFree() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        // null available = MyWealth unreachable or an uninterpretable reply
        when(externalAPIs.isContactUsernameAvailable(any())).thenReturn(availability(null));

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("try again");
        verify(externalAPIs, never()).createCorporateClient(any());
    }

    @Test
    void invalidMyWealthEnumReturnsBadRequestBeforeCallingProvider() {
        CreateCorporateClientRequest request = sampleRequest("NOT_A_VALID_STRUCTURE");

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("Invalid businessStructure");
        verify(externalAPIs, never()).createCorporateClient(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void successfulMyWealthClientResponseReturnsCleanFrontendPayload() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        stubSuccessfulPrimaryUserCreation();

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Corporate account created successfully");

        TokenResponse data = (TokenResponse) response.getBody().getData();
        assertThat(data.getAccessToken()).isEqualTo("corp-user-token");

        ArgumentCaptor<CorporateClient> clientCaptor = ArgumentCaptor.forClass(CorporateClient.class);
        verify(corporateClientRepository, org.mockito.Mockito.atLeastOnce()).save(clientCaptor.capture());
        CorporateClient savedClient = clientCaptor.getAllValues().getLast();
        assertThat(savedClient.getPinHash()).isEqualTo("hashed-value");
        assertThat(savedClient.isPinSet()).isTrue();
        assertThat(savedClient.isPinEnabled()).isTrue();
        verify(corporateNextOfKinRepository, never()).saveAll(any());
        verify(externalAPIs).createAnInvestmentAccount(any());

        // Only ONE MyWealth client is created - the corporate one. No individual client for the rep.
        verify(externalAPIs, never()).createIndividualClient(any(), any(), org.mockito.ArgumentMatchers.anyBoolean(), any());
    }

    @Test
    void investmentAccountIsCreatedAgainstTheCorporateClientNotTheRepresentative() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        stubSuccessfulPrimaryUserCreation();

        corporateAccountService.createCorporateAccount(request, pending());

        ArgumentCaptor<InvestmentAccountRequest> investmentCaptor =
                ArgumentCaptor.forClass(InvestmentAccountRequest.class);
        verify(externalAPIs).createAnInvestmentAccount(investmentCaptor.capture());

        InvestmentAccountRequest investmentRequest = investmentCaptor.getValue();
        assertThat(investmentRequest.getClientId()).isEqualTo("mywealth-client-id");
        assertThat(investmentRequest.getBranchId()).isEqualTo("configured-branch-id");
        assertThat(investmentRequest.getAccountLabel())
                .isEqualTo("Myrtle Test Corporate Client (David Oluwaseun Areegbe)");

        // The local user points at the corporate client and its investment account.
        ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> investmentIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(accountService).createLocalCorporateUser(
                any(User.class), any(), clientIdCaptor.capture(), investmentIdCaptor.capture());

        assertThat(clientIdCaptor.getValue()).isEqualTo("mywealth-client-id");
        assertThat(investmentIdCaptor.getValue()).isEqualTo("investment-account-id");
    }

    @Test
    void primaryContactPasswordIsPassedRawSoPersistUserHashesItExactlyOnce() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        stubSuccessfulPrimaryUserCreation();

        corporateAccountService.createCorporateAccount(request, pending());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(accountService).createLocalCorporateUser(userCaptor.capture(), any(), any(), any());

        User handedOff = userCaptor.getValue();
        assertThat(handedOff.getPassword()).isEqualTo("Password123!");
        assertThat(handedOff.getBvn()).isEqualTo("12345678901");
        assertThat(handedOff.getCorporateClient()).isNotNull();
    }

    @Test
    void successfulCorporateAccountSkipsAbsentNextOfKins() {
        CreateCorporateClientRequest request = sampleRequestWithNextOfKins(null);
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        stubSuccessfulPrimaryUserCreation();

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(corporateNextOfKinRepository, never()).saveAll(any());
    }

    @Test
    void successfulCorporateAccountSavesCorporateNextOfKinsOnly() {
        CreateCorporateClientRequest request = sampleRequestWithNextOfKins();
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        stubSuccessfulPrimaryUserCreation();

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CorporateNextOfKin>> nextOfKinCaptor = ArgumentCaptor.forClass(List.class);
        verify(corporateNextOfKinRepository).saveAll(nextOfKinCaptor.capture());

        List<CorporateNextOfKin> savedNextOfKins = nextOfKinCaptor.getValue();
        assertThat(savedNextOfKins).hasSize(2);
        CorporateNextOfKin first = savedNextOfKins.get(0);
        assertThat(first.getCorporateClient().getId()).isEqualTo("local-corporate-id");
        assertThat(first.getFirstName()).isEqualTo("Jane");
        assertThat(first.getLastName()).isEqualTo("Doe");
        assertThat(first.getEmail()).isEqualTo("jane.doe@example.com");
        assertThat(first.getMobileNo()).isEqualTo("+2348011111111");
        assertThat(first.getRelationship()).isEqualTo("SISTER");
        assertThat(first.isPrimary()).isTrue();
    }

    private void stubSuccessfulPrimaryUserCreation() {
        User createdUser = new User();
        createdUser.setClientId("mywealth-client-id");
        createdUser.setBranchId("branch-id");
        createdUser.setFirstName("David");
        createdUser.setLastName("Areegbe");

        InvestmentAccountResponse investmentAccountResponse = new InvestmentAccountResponse();
        investmentAccountResponse.setId("investment-account-id");

        when(accountService.createLocalCorporateUser(any(User.class), any(), any(), any()))
                .thenReturn(createdUser);
        when(externalAPIs.createAnInvestmentAccount(any())).thenReturn(investmentAccountResponse);
        when(jwtUtils.generateToken(any(User.class))).thenReturn("corp-user-token");
    }

    @Test
    void myWealthResponseParsingFailureReturnsBadGateway() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.createCorporateClient(any())).thenThrow(new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Corporate client was created or processed by MyWealth, but middleware could not parse the response. Check response DTO mapping."
        ));

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("could not parse the response");
        verify(corporateClientRepository, never()).save(any(CorporateClient.class));
    }

    // The business-email duplicate check was intentionally relaxed (see commit "relax the business
    // email constraint" - the check is commented out in validateCorporateAccountDoesNotExist), so a
    // duplicate business email is no longer rejected. The former test asserting that conflict was
    // removed. The primary-contact-email, username, BVN and mobile-number checks below still apply.

    @Test
    void duplicatePrimaryContactEmailReturnsConflictBeforeCallingProvider() {
        assertDuplicateConflict(
                repository -> when(repository.existsByPrimaryContactEmailIgnoreCase("david@example.com")).thenReturn(true),
                "Primary contact email already exists",
                "primaryContact.email"
        );
    }

    @Test
    void duplicatePrimaryContactUsernameReturnsConflictBeforeCallingProvider() {
        assertDuplicateConflict(
                repository -> when(repository.existsByPrimaryContactUsernameIgnoreCase("davidareegbe")).thenReturn(true),
                "Username already exists",
                "primaryContact.username"
        );
    }

    @Test
    void duplicateBvnReturnsConflictBeforeCallingProvider() {
        assertDuplicateConflict(
                repository -> when(repository.existsByBvn("12345678901")).thenReturn(true),
                "BVN already exists",
                "bvn"
        );
    }

    @Test
    void duplicateBusinessMobileNumberReturnsConflictBeforeCallingProvider() {
        assertDuplicateConflict(
                repository -> when(repository.existsByMobileNo("2348012345678")).thenReturn(true),
                "Business mobile number already exists",
                "mobileNo"
        );
    }

    @Test
    void duplicatePrimaryContactMobileNumberReturnsConflictBeforeCallingProvider() {
        assertDuplicateConflict(
                repository -> when(repository.existsByPrimaryContactMobileNo("2348012345678")).thenReturn(true),
                "Primary contact mobile number already exists",
                "primaryContact.mobileNo"
        );
    }

    /*
     * A local save failing AFTER the MyWealth client exists must propagate, not become a response.
     * The surrounding transaction is already doomed at that point, so returning normally makes
     * Spring throw UnexpectedRollbackException at commit and the caller sees an opaque 500.
     * GlobalExceptionHandler maps these to the same statuses the service used to return, so what
     * the caller ultimately sees is unchanged.
     */

    @Test
    void aLocalConstraintFailureAfterUpstreamCreationPropagates() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenThrow(new DataIntegrityViolationException(
                "ERROR: null value in column \"pin_set\" of relation \"corporate_clients\" violates not-null constraint"
        ));

        assertThatThrownBy(() -> corporateAccountService.createCorporateAccount(request, pending()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("pin_set");
    }

    @Test
    void aDuplicateConstraintAfterUpstreamCreationPropagates() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"uk_corporate_clients_email\""
        ));

        assertThatThrownBy(() -> corporateAccountService.createCorporateAccount(request, pending()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_corporate_clients_email");
    }

    @Test
    @Disabled("accountService.assertCorporateUserCanBeCreated is commented out in "
            + "CorporateAccountService.createCorporateAccount (\"todo: uncomment after testing\") - "
            + "re-enable this test when that call is restored.")
    void aDuplicateEmailIsCaughtBeforeMyWealthIsCalledAtAll() {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        org.mockito.Mockito.doThrow(new FunctionException("Account already exists", null))
                .when(accountService).assertCorporateUserCanBeCreated(any(), any());

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Account already exists");
        // the whole point: nothing was created upstream, so the user can fix it and retry
        verify(externalAPIs, never()).createCorporateClient(any());
    }

    private CreateCorporateClientRequest sampleRequest(String businessStructure) {
        return new CreateCorporateClientRequest(
                businessStructure,
                "Myrtle Test Corporate Client",
                "corporateclient@example.com",
                "2348012345678",
                "NGN",
                new CreateCorporateClientRequest.AddressPayload(
                        "12 Adeola Odeku Street",
                        "Victoria Island",
                        "Lagos",
                        "Lagos",
                        "100001",
                        "Nigeria"
                ),
                "2020-01-15",
                "BVN",
                "2030-12-31",
                "12345678901",
                "Software Developer",
                "Nigerian",
                "1234",
                new CreateCorporateClientRequest.ContactPayload(
                        "DIRECTOR",
                        "SINGLE",
                        "MALE",
                        "Mr",
                        "David",
                        "Oluwaseun",
                        "Areegbe",
                        "david@example.com",
                        "2348012345678",
                        "2347012345678",
                        new CreateCorporateClientRequest.ContactAddressPayload(
                                "22 Admiralty Way",
                                "Lekki Phase 1",
                                "Lagos",
                                "Lagos",
                                "100001",
                                "Nigeria"
                        ),
                        "davidareegbe",
                        true,
                        "Password123!",
                        "1998-05-20",
                        null,
                        null,
                        "2024-01-01",
                        "NIMC",
                        "Lagos",
                        "Adebayo",
                        "Osun",
                        "Ilesa West",
                        "1000000-5000000",
                        "1000000-5000000",
                        "500000-2000000",
                        "Business Income",
                        "INTERMEDIATE",
                        "MEDIUM",
                        "Capital Growth",
                        "Qube Wave Technologies",
                        "Lagos, Nigeria"
                ),
                null,
                List.of(),
                null
        );
    }

    private CreateCorporateClientRequest.CorporateNextOfKinPayload nextOfKin(String firstName, Boolean isPrimary) {
        return new CreateCorporateClientRequest.CorporateNextOfKinPayload(
                firstName, null, "Doe", "sibling",
                firstName.toLowerCase() + "@example.com", "2348011111111", "1 Example Street", isPrimary);
    }

    /** Runs account creation with the given next of kin and returns what was persisted. */
    private List<CorporateNextOfKin> persistedNextOfKins(
            List<CreateCorporateClientRequest.CorporateNextOfKinPayload> payloads) {

        when(externalAPIs.createCorporateClient(any())).thenReturn(myWealthResponse());
        when(passwordEncoder.encode(any())).thenReturn("hashed-value");
        when(corporateClientRepository.save(any(CorporateClient.class))).thenAnswer(invocation -> {
            CorporateClient client = invocation.getArgument(0);
            client.setId("local-corporate-id");
            return client;
        });
        stubSuccessfulPrimaryUserCreation();

        ResponseEntity<ApiResponse> response =
                corporateAccountService.createCorporateAccount(sampleRequestWithNextOfKins(payloads), pending());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CorporateNextOfKin>> captor = ArgumentCaptor.forClass(List.class);
        verify(corporateNextOfKinRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void theFlaggedNextOfKinIsPrimaryEvenWhenItIsNotTheFirstListed() {
        List<CorporateNextOfKin> saved = persistedNextOfKins(List.of(
                nextOfKin("Jane", false),
                nextOfKin("John", true)
        ));

        assertThat(saved).hasSize(2);
        assertThat(saved.get(0).isPrimary()).isFalse();
        assertThat(saved.get(1).getFirstName()).isEqualTo("John");
        assertThat(saved.get(1).isPrimary()).isTrue();
    }

    @Test
    void theFirstNextOfKinIsPrimaryWhenNoneIsFlagged() {
        List<CorporateNextOfKin> saved = persistedNextOfKins(List.of(
                nextOfKin("Jane", false),
                nextOfKin("John", false)
        ));

        assertThat(saved.get(0).isPrimary()).isTrue();
        assertThat(saved.get(1).isPrimary()).isFalse();
    }

    @Test
    void aNullPrimaryFlagIsTreatedAsNotFlagged() {
        List<CorporateNextOfKin> saved = persistedNextOfKins(List.of(
                nextOfKin("Jane", null),
                nextOfKin("John", true)
        ));

        assertThat(saved.get(0).isPrimary()).isFalse();
        assertThat(saved.get(1).isPrimary()).isTrue();
    }

    @Test
    void onlyTheFirstFlaggedNextOfKinIsPrimaryWhenSeveralAreFlagged() {
        List<CorporateNextOfKin> saved = persistedNextOfKins(List.of(
                nextOfKin("Jane", true),
                nextOfKin("John", true),
                nextOfKin("Jack", true)
        ));

        assertThat(saved.get(0).isPrimary()).isTrue();
        assertThat(saved.get(1).isPrimary()).isFalse();
        assertThat(saved.get(2).isPrimary()).isFalse();
    }

    @Test
    void exactlyOneNextOfKinIsAlwaysPrimary() {
        List<CorporateNextOfKin> saved = persistedNextOfKins(List.of(
                nextOfKin("Jane", false),
                nextOfKin("John", true),
                nextOfKin("Jack", false)
        ));

        assertThat(saved.stream().filter(CorporateNextOfKin::isPrimary)).hasSize(1);
    }

    private CreateCorporateClientRequest sampleRequestWithNextOfKins() {
        return sampleRequestWithNextOfKins(List.of(
                new CreateCorporateClientRequest.CorporateNextOfKinPayload(
                        " Jane ",
                        null,
                        " Doe ",
                        " sister ",
                        " JANE.DOE@EXAMPLE.COM ",
                        " +2348011111111 ",
                        " 1 Example Street ",
                        true
                ),
                new CreateCorporateClientRequest.CorporateNextOfKinPayload(
                        "John",
                        null,
                        "Doe",
                        "brother",
                        "john.doe@example.com",
                        "2348022222222",
                        "2 Example Street",
                        false
                )
        ));
    }

    private CreateCorporateClientRequest sampleRequestWithNextOfKins(
            List<CreateCorporateClientRequest.CorporateNextOfKinPayload> nextOfKins
    ) {
        CreateCorporateClientRequest base = sampleRequest("MULTI_MEMBER_LLC");
        return new CreateCorporateClientRequest(
                base.businessStructure(),
                base.businessName(),
                base.email(),
                base.mobileNo(),
                base.valuationCurrency(),
                base.businessAddress(),
                base.businessIncDate(),
                base.idType(),
                base.idExpDate(),
                base.bvn(),
                base.profession(),
                base.nationality(),
                base.pin(),
                base.primaryContact(),
                null,
                nextOfKins,
                base.inviteCode()
        );
    }

    private void assertDuplicateConflict(
            Consumer<CorporateClientRepository> duplicateStub,
            String expectedMessage,
            String expectedField
    ) {
        CreateCorporateClientRequest request = sampleRequest("MULTI_MEMBER_LLC");
        duplicateStub.accept(corporateClientRepository);

        ResponseEntity<ApiResponse> response = corporateAccountService.createCorporateAccount(request, pending());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo(expectedMessage);
        Map<?, ?> data = (Map<?, ?>) response.getBody().getData();
        assertThat(data.get("field")).isEqualTo(expectedField);
        verify(externalAPIs, never()).createCorporateClient(any());
        verify(corporateClientRepository, never()).save(any(CorporateClient.class));
    }

    private MyWealthClientResponse myWealthResponse() {
        return new MyWealthClientResponse(
                "mywealth-client-id",
                "MWC0001",
                "OMS00001",
                "CORPORATE",
                "MULTI_MEMBER_LLC",
                "Myrtle Test Corporate Client",
                "corporateclient@example.com",
                "corporateclient@example.com",
                "2348012345678",
                "NGN",
                "configured-group-id",
                "PENDING",
                "PENDING",
                true,
                false,
                LocalDate.parse("2020-01-15"),
                null,
                null,
                null,
                List.of(),
                List.of()
        );
    }
}
