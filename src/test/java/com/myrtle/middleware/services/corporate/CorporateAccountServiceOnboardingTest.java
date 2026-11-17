package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.dtos.bvn.BVNRequest;
import com.myrtle.middleware.dtos.bvn.QoreIdBVNResponse;
import com.myrtle.middleware.dtos.request.BasicDetails;
import com.myrtle.middleware.dtos.request.CorporateCredentialsRequest;
import com.myrtle.middleware.dtos.request.MemartFileRequest;
import com.myrtle.middleware.dtos.request.ValidateBvnRequest;
import com.myrtle.middleware.dtos.request.ValidteOtpForCorporate;
import com.myrtle.middleware.dtos.request.DirectorPayload;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.QoreIdCacResponse;
import com.myrtle.middleware.dtos.response.UsernameAvailabilityResponse;
import com.myrtle.middleware.enums.Gender;
import com.myrtle.middleware.exceptions.ForbiddenException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import com.myrtle.middleware.security.AuthenticatedAccountPrincipal;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.repository.BvnCacheRepo;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.PendingCorporateDataRepository;
import com.myrtle.middleware.repository.PendingCorporateDirectorRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.BvnCheckService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorporateAccountServiceOnboardingTest {

    @Mock private EmailService emailService;
    @Mock private ExternalAPIs externalAPIs;
    @Mock private CorporateClientRepository corporateClientRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtils jwtUtils;
    @Mock private BvnCheckService bvnCheckService;
    @Mock private BvnCacheRepo bvnCacheRepo;
    @Mock private PendingCorporateDataRepository pendingCorporateDataRepository;
    @Mock private PendingCorporateDirectorRepository pendingCorporateDirectorRepository;
    @Mock private AuthService authService;

    private CorporateAccountService corporateAccountService;

    @BeforeEach
    void setUp() {
        ResponseUtil responseUtil = new ResponseUtil();
        corporateAccountService = new CorporateAccountService(
                externalAPIs,
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
                otpRepository,
                emailService,
                bvnCheckService,
                bvnCacheRepo,
                authService,
                pendingCorporateDataRepository,
                pendingCorporateDirectorRepository,
                org.mockito.Mockito.mock(com.myrtle.middleware.services.refer.ReferralService.class)
        );
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAsOnboarding(String pendingId, String email) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccountPrincipal(
                        pendingId, email, Role.USER,
                        AccountType.PENDING_CORPORATE_ONBOARDING, null, null, true),
                null, List.of()));
    }

    private UsernameAvailabilityResponse availability(Boolean available) {
        UsernameAvailabilityResponse response = new UsernameAvailabilityResponse();
        response.setAvailable(available);
        return response;
    }

    private BasicDetails basicDetails() {
        return BasicDetails.builder()
                .firstName("David")
                .lastName("Areegbe")
                .email("david@company.com")
                .phoneNumber("2348012345678")
                .gender(Gender.MALE)
                .build();
    }

    private DirectorPayload director(String firstName, String lastName, String bvn) {
        return new DirectorPayload(
                firstName, null, lastName, "director@company.com", "2348012345678", bvn,
                null, false, "MALE", null, "1 Example Street", null, "Lagos", "Lagos",
                "100001", "NG", "1990-01-01", "SINGLE");
    }

    /*
     * The endpoint takes a bare JSON array, and @Valid on a List parameter does not cascade into its
     * elements - so DirectorPayload's own @NotBlank/@Pattern never fire here. These pin the explicit
     * check that replaces them.
     */

    @Test
    void aDirectorWithAMalformedBvnIsRejectedAndNamed() {
        ResponseEntity<ApiResponse> response = corporateAccountService.collectDirectors(List.of(
                director("Ada", "Obi", "12345678901"),
                director("John", "Doe", "1234")
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("John Doe").contains("11 digits");

        // rejected before the staged record is even loaded, let alone written
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void aNonNumericBvnIsRejected() {
        ResponseEntity<ApiResponse> response = corporateAccountService.collectDirectors(List.of(
                director("Ada", "Obi", "1234567890a")
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("Ada Obi");
    }

    @Test
    void aMissingDirectorBvnIsRejected() {
        ResponseEntity<ApiResponse> response = corporateAccountService.collectDirectors(List.of(
                director("Ada", "Obi", null)
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("BVN is required").contains("Ada Obi");
    }

    @Test
    void anUnnamedDirectorIsIdentifiedByPosition() {
        ResponseEntity<ApiResponse> response = corporateAccountService.collectDirectors(List.of(
                director("Ada", "Obi", "12345678901"),
                director(null, null, "oops")
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("director 2");
    }

    @Test
    void anEmptyDirectorListIsRejected() {
        ResponseEntity<ApiResponse> response = corporateAccountService.collectDirectors(List.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("At least one director");
    }

    // ========== /validate-otp: OTP validation + token issuance ==========

    private ValidteOtpForCorporate otpPayload() {
        return ValidteOtpForCorporate.builder()
                .otp("123456")
                .otpReference("ref-123")
                .email("david@company.com")
                .build();
    }

    private UserOtp freshOtp() {
        UserOtp userOtp = new UserOtp();
        userOtp.setEmail("david@company.com");
        userOtp.setOtp("hashed-otp");
        userOtp.setOtpReference("ref-123");
        return userOtp;
    }

    @Test
    void validateOtpRejectsAnAlreadyRegisteredEmail() {
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com"))
                .thenReturn(Optional.of(new com.myrtle.middleware.model.CorporateClient()));

        ResponseEntity<ApiResponse> response = corporateAccountService.validateOtpAndGetToken(otpPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(otpRepository, never()).findByOtpReference(any());
    }

    @Test
    void validateOtpRejectsAnInvalidCode() {
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(otpRepository.findByOtpReference("ref-123")).thenReturn(Optional.of(freshOtp()));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(false);

        ResponseEntity<ApiResponse> response = corporateAccountService.validateOtpAndGetToken(otpPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(jwtUtils, never()).generatePendingCorporateOnboardingToken(any());
    }

    @Test
    void validateOtpRejectsAnOnboardingAlreadySubmittedToMyWealth() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setExternalClientId("mywealth-client-id");

        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(otpRepository.findByOtpReference("ref-123")).thenReturn(Optional.of(freshOtp()));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(pendingCorporateDataRepository.findByEmailIgnoreCase("david@company.com"))
                .thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response = corporateAccountService.validateOtpAndGetToken(otpPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("already been submitted");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void validateOtpOnAFreshEmailCreatesTheStagedRecordAndSendsToPageOne() {
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(otpRepository.findByOtpReference("ref-123")).thenReturn(Optional.of(freshOtp()));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(pendingCorporateDataRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generatePendingCorporateOnboardingToken(any(PendingCorporateData.class)))
                .thenReturn("onboarding-token");

        ResponseEntity<ApiResponse> response = corporateAccountService.validateOtpAndGetToken(otpPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data.get("token")).isEqualTo("onboarding-token");
        assertThat(data.get("page")).isEqualTo(1);

        ArgumentCaptor<PendingCorporateData> savedCaptor = ArgumentCaptor.forClass(PendingCorporateData.class);
        verify(pendingCorporateDataRepository).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().isEmailVerified()).isTrue();

        ArgumentCaptor<UserOtp> otpCaptor = ArgumentCaptor.forClass(UserOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().isVerified()).isTrue();
    }

    @Test
    void validateOtpOnAResumedOnboardingReportsTheFirstIncompletePage() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setFirstName("David");
        pending.setLastName("Areegbe");
        pending.setBvn("12345678901");
        pending.setCacVerified(true);
        // memartUrl still missing -> resume at page 5

        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(otpRepository.findByOtpReference("ref-123")).thenReturn(Optional.of(freshOtp()));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(pendingCorporateDataRepository.findByEmailIgnoreCase("david@company.com"))
                .thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generatePendingCorporateOnboardingToken(any(PendingCorporateData.class)))
                .thenReturn("onboarding-token");

        ResponseEntity<ApiResponse> response = corporateAccountService.validateOtpAndGetToken(otpPayload());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().getData();
        assertThat(data.get("page")).isEqualTo(5);
    }

    // ========== /preview: requires the onboarding token, echoes the staged record ==========

    @Test
    void previewRequiresTheOnboardingToken() {
        assertThatThrownBy(() -> corporateAccountService.getPendingCorporateData())
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void previewReturnsTheStagedRecordWithBvnMaskedAndNoCredentials() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setFirstName("David");
        pending.setLastName("Areegbe");
        pending.setBvn("12345678901");
        pending.setCompanyName("Acme Limited");
        pending.setPassword("Password123!");
        pending.setPin("1234");

        com.myrtle.middleware.model.PendingCorporateDirector directorEntity =
                new com.myrtle.middleware.model.PendingCorporateDirector();
        directorEntity.setFirstName("Ada");
        directorEntity.setLastName("Obi");
        directorEntity.setEmail("ada@acme.com");
        directorEntity.setPhoneNumber("2348022222222");
        directorEntity.setBvn("22345678901");
        pending.replaceDirectors(List.of(directorEntity));

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDirectorRepository.findByPendingCorporateDataId("pending-id"))
                .thenReturn(pending.getDirectors());

        ResponseEntity<ApiResponse> response = corporateAccountService.getPendingCorporateData();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Object data = response.getBody().getData();
        assertThat(data).isInstanceOf(com.myrtle.middleware.dtos.response.CorporateOnboardingPreviewResponse.class);

        com.myrtle.middleware.dtos.response.CorporateOnboardingPreviewResponse preview =
                (com.myrtle.middleware.dtos.response.CorporateOnboardingPreviewResponse) data;
        assertThat(preview.firstName()).isEqualTo("David");
        assertThat(preview.companyName()).isEqualTo("Acme Limited");
        assertThat(preview.maskedBvn()).isEqualTo("*********01");
        assertThat(preview.directors()).hasSize(1);
        assertThat(preview.directors().get(0).maskedBvn()).isEqualTo("*********01");
        // password/pin are write-only credentials, never echoed back
        assertThat(data.toString()).doesNotContain("Password123!").doesNotContain("1234");
    }

    // ========== /basic-details: now requires the onboarding token ==========

    @Test
    void collectBasicDetailsRequiresTheOnboardingToken() {
        assertThatThrownBy(() -> corporateAccountService.collectBasicDetails(basicDetails()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void collectBasicDetailsUpdatesTheTokenHoldersRecord() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pending.getFirstName()).isEqualTo("David");
        assertThat(pending.getGender()).isEqualTo(Gender.MALE);
        // OTP was already validated at /validate-otp - this step sends no new OTP
        verify(emailService, never()).deliverOtp(anyString(), any());
    }

    @Test
    void collectBasicDetailsRejectsAnEmailThatAlreadyHasACorporateAccount() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com"))
                .thenReturn(Optional.of(new com.myrtle.middleware.model.CorporateClient()));

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void collectBasicDetailsRejectsAnOnboardingAlreadySubmittedToMyWealth() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setExternalClientId("mywealth-client-id");

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("already been submitted");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    @Disabled("MyWealth username-availability check is commented out in CorporateAccountService.collectBasicDetails "
            + "pending a fix on MyWealth's side - re-enable this test when that call is restored.")
    void anEmailAlreadyRegisteredOnMyWealthIsRejectedAtTheFirstStep() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(externalAPIs.isContactUsernameAvailable("david@company.com")).thenReturn(availability(false));

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("already exists");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    @Disabled("MyWealth username-availability check is commented out in CorporateAccountService.collectBasicDetails "
            + "pending a fix on MyWealth's side - re-enable this test when that call is restored.")
    void anUnconfirmableEmailStopsOnboardingRatherThanStartingIt() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(externalAPIs.isContactUsernameAvailable("david@company.com")).thenReturn(availability(null));

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("try again");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    // ========== /validate-bvn: also requires the onboarding token ==========

    @Test
    void insureBvnIsCorrectRequiresTheOnboardingToken() {
        assertThatThrownBy(() -> corporateAccountService.insureBvnIsCorrect(new ValidateBvnRequest("12345678901")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void insureBvnIsCorrectIsRejectedBeforeThePaidCallWhenBasicDetailsAreIncomplete() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setGender(null);

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.insureBvnIsCorrect(new ValidateBvnRequest("12345678901"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("basic details again");
        verify(externalAPIs, never()).verifyBvnPremium(any());
    }

    @Test
    void insureBvnIsCorrectVerifiesAgainstQoreIdAndStoresTheResult() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setFirstName("David");
        pending.setLastName("Areegbe");
        pending.setPhoneNumber("2348012345678");
        pending.setGender(Gender.MALE);

        QoreIdBVNResponse qoreIdBvnResponse = QoreIdBVNResponse.builder()
                .bvn(QoreIdBVNResponse.Bvn.builder().middlename("Middle").build())
                .build();

        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(externalAPIs.verifyBvnPremium(any())).thenReturn(qoreIdBvnResponse);
        doNothing().when(bvnCheckService).bvnValidationAlgorithm(any(QoreIdBVNResponse.class), any(BVNRequest.class));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.insureBvnIsCorrect(new ValidateBvnRequest("12345678901"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pending.getBvn()).isEqualTo("12345678901");
        verify(authService).cacheBvn(qoreIdBvnResponse.getBvn(), "12345678901");
        verify(pendingCorporateDataRepository).save(pending);
    }

    // ========== PATCH steps: partial edits until the onboarding is completed ==========

    private PendingCorporateData stagedRecord() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        pending.setFirstName("David");
        pending.setMiddleName("Oluwaseun");
        pending.setLastName("Areegbe");
        pending.setPhoneNumber("2348012345678");
        pending.setGender(Gender.MALE);
        return pending;
    }

    @Test
    void updateBasicDetailsRequiresTheOnboardingToken() {
        assertThatThrownBy(() -> corporateAccountService.updateBasicDetails(basicDetails()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateBasicDetailsOnlyOverwritesTheNonNullFields() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Only the last name is supplied; everything else is null and must be left untouched.
        BasicDetails patch = BasicDetails.builder().lastName("Adeyemi").build();
        ResponseEntity<ApiResponse> response = corporateAccountService.updateBasicDetails(patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getLastName()).isEqualTo("Adeyemi");
        assertThat(pending.getFirstName()).isEqualTo("David");       // unchanged
        assertThat(pending.getMiddleName()).isEqualTo("Oluwaseun");  // unchanged
        assertThat(pending.getPhoneNumber()).isEqualTo("2348012345678"); // unchanged
        assertThat(pending.getGender()).isEqualTo(Gender.MALE);      // unchanged
    }

    @Test
    void aPatchIsRejectedOnceTheOnboardingHasBeenSubmittedToMyWealth() {
        PendingCorporateData pending = stagedRecord();
        pending.setExternalClientId("mywealth-client-id");
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.updateBasicDetails(BasicDetails.builder().lastName("Adeyemi").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("already been submitted");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void updateMemartWithNoUrlIsRejected() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.updateMemart(new MemartFileRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void updateMemartStoresTheNewUrl() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.updateMemart(new MemartFileRequest("https://x/memart-v2.pdf"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getMemartUrl()).isEqualTo("https://x/memart-v2.pdf");
    }

    @Test
    void updateDirectorsClearsTheOldRowsBeforeSavingTheNewList() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response = corporateAccountService.updateDirectors(List.of(
                director("Ada", "Obi", "22345678901")
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(pendingCorporateDirectorRepository).deleteByPendingCorporateDataId("pending-id");
        verify(pendingCorporateDirectorRepository).saveAll(any());
    }

    @Test
    void updateDirectorsStillValidatesBvns() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response = corporateAccountService.updateDirectors(List.of(
                director("Ada", "Obi", "1234")
        ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("11 digits");
        verify(pendingCorporateDirectorRepository, never()).deleteByPendingCorporateDataId(any());
    }

    @Test
    void updateCorporateCredentialsMergesOnlyTheSuppliedFields() {
        PendingCorporateData pending = stagedRecord();
        pending.setPin("1234");
        pending.setPassword("OldPassword1!");
        pending.setProfession("DIRECTOR");
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Only the profession is supplied; pin and password must be left as they were.
        CorporateCredentialsRequest patch = new CorporateCredentialsRequest(
                null, null, "ACCOUNTANT", null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.updateCorporateCredentials(patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getProfession()).isEqualTo("ACCOUNTANT");
        assertThat(pending.getPin()).isEqualTo("1234");            // unchanged
        assertThat(pending.getPassword()).isEqualTo("OldPassword1!"); // unchanged
    }

    @Test
    void updateBvnWithNoBvnIsRejectedBeforeAnyVerification() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        ResponseEntity<ApiResponse> response =
                corporateAccountService.updateBvn(new ValidateBvnRequest(null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(externalAPIs, never()).verifyBvnPremium(any());
    }

    // ========== Early completion validations: fail fast, so /complete cannot fail late ==========

    @Test
    void basicDetailsRejectsADuplicatePrimaryContactEmailUpFront() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(corporateClientRepository.existsByPrimaryContactEmailIgnoreCase("david@company.com")).thenReturn(true);

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("Primary contact email already exists");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void basicDetailsRejectsADuplicateMobileNumberUpFront() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId("pending-id");
        pending.setEmail("david@company.com");
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.findByEmailIgnoreCase("david@company.com")).thenReturn(Optional.empty());
        when(corporateClientRepository.existsByMobileNo(any())).thenReturn(true);

        ResponseEntity<ApiResponse> response = corporateAccountService.collectBasicDetails(basicDetails());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("Mobile number already exists");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void validateBvnRejectsABvnAlreadyRegisteredBeforeSpendingAQoreIdCall() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.existsByBvn("12345678901")).thenReturn(true);

        ResponseEntity<ApiResponse> response =
                corporateAccountService.insureBvnIsCorrect(new ValidateBvnRequest("12345678901"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("BVN already exists");
        verify(externalAPIs, never()).verifyBvnPremium(any());
    }

    @Test
    void kybRejectsIncompleteCacDataSoCompletionCannotFailLater() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // CAC returns a company name but no address/state/incorporation date.
        QoreIdCacResponse cacResponse = new QoreIdCacResponse();
        cacResponse.setCac(Map.of("rcNumber", "RC123456", "companyName", "Acme Ventures Limited"));
        when(externalAPIs.verifyCacPremium(any())).thenReturn(cacResponse);

        ResponseEntity<ApiResponse> response = corporateAccountService.verifyAndCollectKYB(
                com.myrtle.middleware.dtos.request.BusinessInformation.builder().rcNumber("RC123456").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).containsAnyOf("business address", "incorporation date", "company name");
    }

    @Test
    void kybAcceptsCompleteCacData() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        QoreIdCacResponse cacResponse = new QoreIdCacResponse();
        cacResponse.setCac(Map.of(
                "rcNumber", "RC123456",
                "companyName", "Acme Ventures Limited",
                "headOfficeAddress", "22 Admiralty Way",
                "state", "Lagos",
                "registrationDate", "2019-04-12T00:00:00Z"));
        when(externalAPIs.verifyCacPremium(any())).thenReturn(cacResponse);

        ResponseEntity<ApiResponse> response = corporateAccountService.verifyAndCollectKYB(
                com.myrtle.middleware.dtos.request.BusinessInformation.builder().rcNumber("RC123456").build());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void credentialsRejectAnUnrecognisedFinancialProfileValueUpFront() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        // "SUPER_EXPERT" is not a recognised investmentExperience - /complete would have thrown.
        CorporateCredentialsRequest request = new CorporateCredentialsRequest(
                "1234", "Password123!", "DIRECTOR", "Nigerian", "2030-12-31",
                null, null, null, null, null, null, null, null, null, null, null,
                "SUPER_EXPERT", null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.collectCorporateCredentials(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("investmentExperience");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void credentialsRejectADuplicateCustomUsernameUpFront() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(corporateClientRepository.existsByPrimaryContactUsernameIgnoreCase(any())).thenReturn(true);

        CorporateCredentialsRequest request = new CorporateCredentialsRequest(
                "1234", "Password123!", "DIRECTOR", "Nigerian", "2030-12-31",
                null, null, "taken-handle", null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.collectCorporateCredentials(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getMessage()).contains("Username already exists");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void patchCredentialsRejectsAPinThatIsNotFourDigits() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        CorporateCredentialsRequest patch = new CorporateCredentialsRequest(
                "123", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.updateCorporateCredentials(patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("4 digits");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void patchCredentialsRejectsANonNumericPin() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        CorporateCredentialsRequest patch = new CorporateCredentialsRequest(
                "abcd", null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.updateCorporateCredentials(patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("4 digits");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void patchCredentialsRejectsAPasswordShorterThanEightCharacters() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));

        CorporateCredentialsRequest patch = new CorporateCredentialsRequest(
                null, "short", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.updateCorporateCredentials(patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getMessage()).contains("at least 8 characters");
        verify(pendingCorporateDataRepository, never()).save(any(PendingCorporateData.class));
    }

    @Test
    void patchCredentialsAcceptsAValidFourDigitPinAndLongPassword() {
        PendingCorporateData pending = stagedRecord();
        authenticateAsOnboarding("pending-id", "david@company.com");
        when(pendingCorporateDataRepository.findById("pending-id")).thenReturn(Optional.of(pending));
        when(pendingCorporateDataRepository.save(any(PendingCorporateData.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CorporateCredentialsRequest patch = new CorporateCredentialsRequest(
                "4321", "Password123!", null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ResponseEntity<ApiResponse> response = corporateAccountService.updateCorporateCredentials(patch);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getPin()).isEqualTo("4321");
        assertThat(pending.getPassword()).isEqualTo("Password123!");
    }
}
