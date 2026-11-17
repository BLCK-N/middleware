package com.myrtle.middleware.services.corporate;

import com.myrtle.middleware.dtos.response.*;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Gender;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.exceptions.ForbiddenException;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.PendingCorporateDirector;
import com.myrtle.middleware.repository.*;
import com.myrtle.middleware.security.AuthenticatedAccountPrincipal;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.BvnCheckService;
import com.myrtle.middleware.utils.JwtUtils;
import com.myrtle.middleware.utils.ResponseUtil;
import com.myrtle.middleware.utils.ValidatorUitls;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * The GET counterparts of the onboarding form pages: they must return what was staged, never the
 * write-only credentials, and never anything at all without the onboarding token.
 */
@ExtendWith(MockitoExtension.class)
class CorporateOnboardingReadEndpointsTest {

    private static final String PENDING_ID = "pending-id";
    private static final String EMAIL = "david@company.com";

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

    @Test
    void basicDetailsAreReadBackAsSubmitted() {
        stageOnboarding(fullyStagedRecord());

        ResponseEntity<ApiResponse> response =
                corporateAccountService.getBasicDetails();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CorporateBasicDetailsResponse data = (CorporateBasicDetailsResponse) response.getBody().getData();
        assertThat(data.collected()).isTrue();
        assertThat(data.firstName()).isEqualTo("David");
        assertThat(data.email()).isEqualTo(EMAIL);
        assertThat(data.gender()).isEqualTo("MALE");
    }

    @Test
    void anUntouchedPageReportsItselfAsNotCollected() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId(PENDING_ID);
        pending.setEmail(EMAIL);
        stageOnboarding(pending);

        CorporateBasicDetailsResponse basic =
                (CorporateBasicDetailsResponse) corporateAccountService.getBasicDetails().getBody().getData();
        CorporateMemartResponse memart =
                (CorporateMemartResponse) corporateAccountService.getMemartFile().getBody().getData();
        CorporateDirectorsResponse directors =
                (CorporateDirectorsResponse) corporateAccountService.getDirectors().getBody().getData();

        assertThat(basic.collected()).isFalse();
        assertThat(memart.collected()).isFalse();
        assertThat(memart.url()).isNull();
        assertThat(directors.collected()).isFalse();
        assertThat(directors.directors()).isEmpty();
    }

    @Test
    void theBvnIsMaskedOnTheWayBackOut() {
        stageOnboarding(fullyStagedRecord());

        CorporateBvnDetailsResponse data =
                (CorporateBvnDetailsResponse) corporateAccountService.getBvnDetails().getBody().getData();

        assertThat(data.verified()).isTrue();
        assertThat(data.maskedBvn()).isEqualTo("*********01");
        assertThat(data.dateOfBirth()).isEqualTo("1990-01-01");
    }

    @Test
    void credentialsAreReportedAsSetWithoutReturningThem() {
        stageOnboarding(fullyStagedRecord());

        ResponseEntity<ApiResponse> response =
                corporateAccountService.getCorporateCredentials();
        CorporateCredentialsDetailsResponse data =
                (CorporateCredentialsDetailsResponse) response.getBody().getData();

        assertThat(data.pinSet()).isTrue();
        assertThat(data.passwordSet()).isTrue();
        assertThat(data.collected()).isTrue();
        assertThat(data.profession()).isEqualTo("Engineer");
        // no field on the record can carry the secrets themselves
        assertThat(data.toString()).doesNotContain("1234").doesNotContain("s3cretPassword");
    }

    @Test
    void directorsComeBackWithMaskedBvns() {
        stageOnboarding(fullyStagedRecord());

        CorporateDirectorsResponse data =
                (CorporateDirectorsResponse) corporateAccountService.getDirectors().getBody().getData();

        assertThat(data.count()).isEqualTo(1);
        assertThat(data.directors().get(0).firstName()).isEqualTo("Ada");
        assertThat(data.directors().get(0).maskedBvn()).isEqualTo("*********01");
    }

    @Test
    void theFullSubmissionCarriesEveryPageAndTheResumePage() {
        stageOnboarding(fullyStagedRecord());

        CorporateOnboardingSubmissionResponse data =
                (CorporateOnboardingSubmissionResponse) corporateAccountService.getFullSubmission().getBody().getData();

        assertThat(data.email()).isEqualTo(EMAIL);
        assertThat(data.basicDetails().firstName()).isEqualTo("David");
        assertThat(data.bvn().verified()).isTrue();
        assertThat(data.kyb().cacVerified()).isTrue();
        assertThat(data.memart().url()).isEqualTo("https://example.com/memart.pdf");
        assertThat(data.directors().count()).isEqualTo(1);
        assertThat(data.credentials().pinSet()).isTrue();
        assertThat(data.progress().outstandingSteps()).isEmpty();
        assertThat(data.nextPage()).isEqualTo(8);
    }

    @Test
    void everyReadEndpointRefusesACallerWithoutTheOnboardingToken() {
        assertThatThrownBy(() -> corporateAccountService.getBasicDetails())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> corporateAccountService.getBvnDetails())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> corporateAccountService.getKybDetails())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> corporateAccountService.getMemartFile())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> corporateAccountService.getDirectors())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> corporateAccountService.getCorporateCredentials())
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> corporateAccountService.getFullSubmission())
                .isInstanceOf(ForbiddenException.class);
    }

    private void stageOnboarding(PendingCorporateData pending) {
        authenticateAsOnboarding();
        when(pendingCorporateDataRepository.findById(PENDING_ID)).thenReturn(Optional.of(pending));
        when(pendingCorporateDirectorRepository.findByPendingCorporateDataId(PENDING_ID))
                .thenReturn(pending.getDirectors() == null ? List.of() : List.copyOf(pending.getDirectors()));
    }

    private void authenticateAsOnboarding() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAccountPrincipal(
                        PENDING_ID, EMAIL, Role.USER,
                        AccountType.PENDING_CORPORATE_ONBOARDING, null, null, true),
                null, List.of()));
    }

    private PendingCorporateData fullyStagedRecord() {
        PendingCorporateData pending = new PendingCorporateData();
        pending.setId(PENDING_ID);
        pending.setEmail(EMAIL);
        pending.setEmailVerified(true);
        pending.setFirstName("David");
        pending.setLastName("Areegbe");
        pending.setPhoneNumber("2348012345678");
        pending.setGender(Gender.MALE);
        pending.setBvn("12345678901");
        pending.setDate("1990-01-01");
        pending.setCacVerified(true);
        pending.setRcNumber("RC123456");
        pending.setCompanyName("Acme Ltd");
        pending.setMemartUrl("https://example.com/memart.pdf");
        pending.setPin("1234");
        pending.setPassword("s3cretPassword");
        pending.setProfession("Engineer");
        pending.setNationality("Nigerian");
        pending.replaceDirectors(List.of(director()));
        return pending;
    }

    private PendingCorporateDirector director() {
        PendingCorporateDirector director = new PendingCorporateDirector();
        director.setFirstName("Ada");
        director.setLastName("Obi");
        director.setEmail("ada@company.com");
        director.setPhoneNumber("2348012345678");
        director.setBvn("12345678901");
        return director;
    }
}
