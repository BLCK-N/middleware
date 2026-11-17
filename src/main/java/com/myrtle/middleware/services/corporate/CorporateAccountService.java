package com.myrtle.middleware.services.corporate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myrtle.middleware.dtos.bvn.*;
import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.*;
import com.myrtle.middleware.dtos.response.TokenResponse;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.exceptions.*;
import com.myrtle.middleware.model.*;
import com.myrtle.middleware.repository.*;
import com.myrtle.middleware.security.AuthenticatedAccountPrincipal;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.authentication.AccountService;
import com.myrtle.middleware.services.refer.ReferralService;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.authentication.PendingOnboardingService;
import com.myrtle.middleware.services.authentication.RefreshTokenService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CorporateAccountService {

    private static final int BVN_LENGTH = 11;
    public static final int MAX_CORPORATE_USERS = 5;

    private static final Set<String> STORED_PAYLOAD_EXCLUDED_FIELDS = Set.of("password", "pin");
    private static final Set<String> CORPORATE_CLIENT_UNIQUE_CONSTRAINTS = Set.of(
            "uk_corporate_clients_email",
            "uk_corporate_clients_bvn",
            "uk_corporate_clients_mobile_no",
            "uk_corporate_clients_primary_contact_email",
            "uk_corporate_clients_primary_contact_username",
            "uk_corporate_clients_primary_contact_mobile_no",
            "uk_corporate_clients_ref_code"
    );

    private final ExternalAPIs externalAPIs;
    private final CorporateClientRepository corporateClientRepository;
    private final CorporateNextOfKinRepository corporateNextOfKinRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResponseUtil responseUtil;
    private final JwtUtils jwtUtil;
    private final ObjectMapper objectMapper;
    private final ValidatorUitls validatorUitls;
    private final AccountService accountService;
    private final CorporateOnboardingCheckpoint checkpoint;
    private final CorporateDirectorRepository corporateDirectorRepository;
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final EmailService emailService;
    private final BvnCheckService bvnCheckService;
    private final BvnCacheRepo bvnCacheRepo;
    private final AuthService authService;
    private final PendingCorporateDataRepository pendingCorporateDataRepository;
    private final PendingCorporateDirectorRepository pendingCorporateDirectorRepository;
    private final ReferralService referralService;

    @Lazy
    @Autowired
    private PendingOnboardingService pendingOnboardingService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Value("${mywealth.client.group.id}")
    private String clientGroupId;

    @Value("${mywealth.branch.id}")
    private String branchId;


    public ResponseEntity<?> sendSimpleOtp(String email) {
        String reference = UUID.randomUUID().toString();
        authService.sendNewOtp(email, reference);

        HashMap<String, String> responseData = new HashMap<>();
        responseData.put("otpReference", reference);
        responseData.put("email", email);
        return responseUtil.success("Validate OTP to continue", responseData);
    }

    // Step - 0: Validate OTP and issue the onboarding token every later stage requires. Also
    // reports which page the caller should resume from, so a caller who dropped off mid-onboarding
    // is sent straight back to where they left off instead of restarting at basic-details.
    public ResponseEntity<ApiResponse> validateOtpAndGetToken(ValidteOtpForCorporate request) {
        if (request.getEmail() == null || request.getOtp() == null || request.getOtpReference() == null) {
            return responseUtil.badRequest("Email, OTP and OTP reference are required", null);
        }

        String email = normalizeEmail(request.getEmail());

        if (corporateClientRepository.findByEmailIgnoreCase(email).isPresent()) {
            return responseUtil.conflict("Corporate account already exists", null);
        }

        Optional<ResponseEntity<ApiResponse>> otpVerificationError =
                verifyEmailOtp(email, request.getOtp(), request.getOtpReference());
        if (otpVerificationError.isPresent()) {
            return otpVerificationError.get();
        }

        PendingCorporateData pendingCorporateData = pendingCorporateDataRepository.findByEmailIgnoreCase(email)
                .orElseGet(PendingCorporateData::new);

        if (StringUtils.hasText(pendingCorporateData.getExternalClientId())) {
            log.warn("Rejected OTP validation for an onboarding already created on MyWealth | email={}", email);
            return responseUtil.conflict(
                    "This corporate onboarding has already been submitted. Please continue where you left off,"
                            + " or contact " + CorporateUserPolicy.SUPPORT_EMAIL + " for help.", null);
        }

        pendingCorporateData.setEmail(email);
        pendingCorporateData.setEmailVerified(true);
        pendingCorporateDataRepository.save(pendingCorporateData);
        trackCorporateStarted(pendingCorporateData);
        trackCorporateProgress(pendingCorporateData);

        String accessToken = jwtUtil.generatePendingCorporateOnboardingToken(pendingCorporateData);
        int nextPage = determineNextOnboardingPage(pendingCorporateData);

        Map<String, Object> data = new HashMap<>();
        data.put("token", accessToken);
        data.put("page", nextPage);

        log.info("Corporate OTP verified, onboarding token issued | email={} | nextPage={}", email, nextPage);
        return responseUtil.success("OTP verified successfully", data);
    }

    /**
     * Page numbers match {@link com.myrtle.middleware.controller.CorporateAccountController}'s
     * step comments, so a caller resuming an in-progress onboarding is sent to the first
     * incomplete step rather than restarting from page 1.
     */
    private int determineNextOnboardingPage(PendingCorporateData pending) {
        if (!StringUtils.hasText(pending.getFirstName())) {
            return 1; // basic-details
        }
        if (!StringUtils.hasText(pending.getBvn())) {
            return 2; // validate-bvn
        }
        if (!pending.isCacVerified()) {
            return 4; // kyb
        }
        if (!StringUtils.hasText(pending.getMemartUrl())) {
            return 5; // memart
        }
        List<PendingCorporateDirector> directors = pending.getId() == null
                ? List.of()
                : pendingCorporateDirectorRepository.findByPendingCorporateDataId(pending.getId());
        if (directors.isEmpty()) {
            return 6; // directors
        }
        if (!StringUtils.hasText(pending.getPassword())) {
            return 7; // credentials
        }
        return 8; // complete
    }

    private void trackCorporateStarted(PendingCorporateData pending) {
        if (pendingOnboardingService == null) {
            return;
        }
        pendingOnboardingService.startCorporate(pending);
    }

    private void trackCorporateProgress(PendingCorporateData pending) {
        if (pendingOnboardingService == null || pending == null) {
            return;
        }
        pendingOnboardingService.recordCorporateProgress(pending, determineNextOnboardingPage(pending));
    }

    private void trackCorporateAccountCreated(String email, String userId) {
        if (pendingOnboardingService == null) {
            return;
        }
        pendingOnboardingService.markAccountCreated(email, PendingOnboarding.Type.CORPORATE, userId);
    }

    private String buildInvestmentAccountLabel(
            CorporateClient corporateClient,
            CreateCorporateClientRequest.ContactPayload primaryContact
    ) {
        // companyName is only copied across in applyPendingOnboardingData (after this point),
        // so fall back to the label, which toExternalRequest populates from businessName.
        String companyName = firstNonBlank(
                corporateClient.getCompanyName(),
                corporateClient.getLabel(),
                corporateClient.getEmail()
        );

        String repName = primaryContact == null ? null : fullName(
                primaryContact.firstName(),
                primaryContact.middleName(),
                primaryContact.lastName(),
                null
        );

        if (!StringUtils.hasText(companyName)) {
            return StringUtils.hasText(repName) ? repName : "Corporate account";
        }
        if (!StringUtils.hasText(repName) || "Related contact".equals(repName)) {
            return truncate(companyName);
        }
        return truncate(companyName + " (" + repName + ")");
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    public ResponseEntity<ApiResponse> createCorporateAccount(CreateCorporateClientRequest request, PendingCorporateData pending) {

        User user;
        List<NextOfKinRequest> nextOfKins;
        CreateCorporateClientRequest.ExternalPayload externalPayload;
        String agentId;

        boolean resuming = pending != null && StringUtils.hasText(pending.getExternalClientId());
        try {
            validateCorporateRequest(request, resuming);
            agentId = referralService.resolveAgentId(request.inviteCode());
            externalPayload = request.toExternalRequest(clientGroupId, agentId);

            user = buildPrimaryContactUser(request);
            nextOfKins = mapCorporateNextOfKins(request.nextOfKins());

//            accountService.assertCorporateUserCanBeCreated(user, nextOfKins); // todo: uncomment after testing

        } catch (FunctionException e) {
            return responseUtil.fromFunctionException(e);
        } catch (IllegalArgumentException e) {
            return responseUtil.badRequest(e.getMessage(), null);
        } catch (DuplicateResourceException e) {
            return responseUtil.conflict(e.getMessage(), Map.of("field", e.getField()));
        } catch (UpstreamServiceException e) {
            return responseUtil.serviceUnavailable(e.getMessage(), null);
        } catch (ResponseStatusException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_GATEWAY)) {
                return responseUtil.badGateway(e.getReason(), null);
            }
            return responseUtil.serverError(e.getReason(), null);
        } catch (DataIntegrityViolationException e) {
            if (isCorporateClientDuplicateConstraint(e)) {
                log.warn("Corporate account local save duplicate constraint | email={} | bvn={}",
                        request.email(), maskBvn(request.bvn()), e);
                return responseUtil.conflict(
                        "Duplicate corporate account value already exists",
                        Map.of("field", "unknown")
                );
            }

            log.error("Corporate account local save failed | email={} | bvn={}",
                    request.email(), maskBvn(request.bvn()), e);
            return responseUtil.serverError(
                    "We could not save the corporate account locally. Please try again or contact support for assistance.",
                    null
            );
        } catch (Exception e) {
            log.error("Corporate account creation failed | email={} | reason={}", request.email(), e.getMessage(), e);
            return responseUtil.serverError(
                    "We could not complete corporate account creation right now. Please try again or contact support for assistance.",
                    null
            );
        }
        log.info("validations completed. Proceeding to create upstream and persist it.");
        return createUpstreamAndPersist(request, externalPayload, user, nextOfKins, pending, agentId);
    }

    private ResponseEntity<ApiResponse> createUpstreamAndPersist(
            CreateCorporateClientRequest request,
            CreateCorporateClientRequest.ExternalPayload externalPayload,
            User user,
            List<NextOfKinRequest> nextOfKins,
            PendingCorporateData pending,
            String agentId) {

        String pendingId = pending == null ? null : pending.getId();
        MyWealthClientResponse externalClient;
        String externalClientId;

        if (pending != null && StringUtils.hasText(pending.getExternalClientId())) {
            externalClientId = pending.getExternalClientId();
            externalClient = resumedClientResponse(externalClientId);
            log.info("Resuming corporate onboarding against existing MyWealth client | externalClientId={} | email={}",
                    externalClientId, request.email());
        } else {
            try {
                externalClient = externalAPIs.createCorporateClient(externalPayload);
            } catch (ResponseStatusException e) {
                log.error("Corporate client creation failed at MyWealth; a client may exist upstream | email={}",
                        request.email(), e);
                if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_GATEWAY)) {
                    return responseUtil.badGateway(e.getReason(), null);
                }
                return responseUtil.serverError(e.getReason(), null);
            } catch (UpstreamServiceException e) {
                return responseUtil.serviceUnavailable(e.getMessage(), null);
            }

            if (externalClient == null) {
                return responseUtil.serviceUnavailable(
                        "We could not create your corporate account right now. Please try again shortly.",
                        null
                );
            }

            externalClientId = firstNonBlank(externalClient.id(), externalClient.code());
            if (!StringUtils.hasText(externalClientId)) {
                return responseUtil.badGateway(
                        "MyWealth processed the corporate client, but the response did not include a client identifier.",
                        null
                );
            }

            // Committed immediately in its own transaction - see CorporateOnboardingCheckpoint.
            if (pendingId != null) {
                checkpoint.recordExternalClientId(pendingId, externalClientId);
                // Keep the caller's copy in step: it is the same row in the outer persistence
                // context, and flushing it while stale would write the checkpoint back to null.
                pending.setExternalClientId(externalClientId);
            }
        }

        // Past this point a corporate client EXISTS on MyWealth and cannot be removed by us.
        String investmentId = null;
        try {
            CorporateClient corporateClient = saveCorporateClient(request, externalPayload, externalClient);
            int nextOfKinCount = saveCorporateNextOfKinDetails(corporateClient, request);

            if (pending != null && StringUtils.hasText(pending.getExternalInvestmentId())) {
                // Also from a previous attempt - creating another would leave a second live account.
                investmentId = pending.getExternalInvestmentId();
                log.info("Resuming with existing investment account | investmentId={}", investmentId);
            } else {
                // The investment account belongs to the COMPANY, so it is created against the
                // corporate client id. Corporate users are local logins pointing at this same account.
                InvestmentAccountRequest accountRequest = new InvestmentAccountRequest(
                        corporateClient.getExternalClientId(),
                        "",
                        branchId,
                        InvestmentAccountRequest.AccountUsage.LIVE,
                        InvestmentAccountRequest.MgmtType.SELF,
                        buildInvestmentAccountLabel(corporateClient, request.primaryContact()),
                        true,
                        true
                );

                InvestmentAccountResponse investmentResponse = createInvestmentAccount(accountRequest);
                investmentId = investmentResponse.getId();

                if (pendingId != null) {
                    checkpoint.recordInvestmentId(pendingId, investmentId);
                    pending.setExternalInvestmentId(investmentId);
                }
            }

            corporateClient.setInvestmentId(investmentId);
            corporateClient = corporateClientRepository.save(corporateClient);

            user.setCorporateClient(corporateClient);
            User userM = accountService.createLocalCorporateUser(
                    user,
                    nextOfKins,
                    corporateClient.getExternalClientId(),
                    investmentId
            );

            try {
                referralService.recordReferral(agentId, userM.getId(), "CORPORATE");
            } catch (Exception ex) {
                log.warn("recordReferral failed for corporate user {}: {}", userM.getEmailAddress(), ex.getMessage());
            }

            TokenResponse tokenResponse = new TokenResponse(
                    jwtUtil.generateToken(userM),
                    refreshTokenService.issueForUser(userM)
            );

            log.info("Corporate account created | corporateClientId={} | externalClientId={} | nextOfKinCount={}",
                    corporateClient.getId(), corporateClient.getExternalClientId(), nextOfKinCount);

            trackCorporateAccountCreated(
                    firstNonBlank(pending == null ? null : pending.getEmail(), request.email()),
                    userM.getId()
            );

            return responseUtil.created("Corporate account created successfully", tokenResponse);

        } catch (RuntimeException e) {
            // Local state rolls back; these upstream records survive. When a checkpoint was written
            // the next /corporate/complete resumes against them, so no manual work is needed. Without
            // one they are genuinely stranded and need reconciling with MyWealth by hand.
            if (pendingId != null) {
                log.error("RECOVERABLE_MYWEALTH_RECORDS | externalClientId={} | investmentId={} | email={} | reason={}",
                        externalClientId, investmentId, request.email(), e.getMessage(), e);
            } else {
                log.error("ORPHANED_MYWEALTH_RECORDS | externalClientId={} | investmentId={} | email={} | reason={}",
                        externalClientId, investmentId, request.email(), e.getMessage(), e);
            }
            throw e;
        }
    }


    public InvestmentAccountResponse createInvestmentAccount(InvestmentAccountRequest accountRequest) {
        InvestmentAccountResponse investmentAccountResponse = externalAPIs.createAnInvestmentAccount(accountRequest);
        if (investmentAccountResponse == null) {
            throw new FunctionException("An error occurred while creating investment account", null);
        }
        return investmentAccountResponse;
    }

    private MyWealthClientResponse resumedClientResponse(String externalClientId) {
        return new MyWealthClientResponse(
                externalClientId, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }


    private User buildPrimaryContactUser(CreateCorporateClientRequest request) {
        CreateCorporateClientRequest.ContactPayload contact = request.primaryContact();

        User user = new User();
        user.setUsePin(true);
        user.setBvn(request.bvn());
        user.setGender(contact.gender());
        user.setLastName(contact.lastName());
        user.setEmailAddress(contact.email());
        user.setFirstName(contact.firstName());
        user.setPhoneNumber(contact.mobileNo());
        user.setMiddleName(contact.middleName());
        user.setPin(passwordEncoder.encode(request.pin()));
        // Raw password - persistUser() encodes it. Encoding here too would double-hash it and lock
        // the user out of password login.
        user.setPassword(contact.password());
        user.setAddress(contact.address() == null ? null : contact.address().addressLine1());
        return user;
    }

    @Transactional
    public ResponseEntity<ApiResponse> setCorporatePin(String corporateClientId, String pin) {
        if (!StringUtils.hasText(corporateClientId)) {
            return responseUtil.badRequest("Corporate client ID is required", null);
        }

        if (!StringUtils.hasText(pin) || !pin.matches("^\\d{4}$")) {
            return responseUtil.badRequest("PIN must be exactly 4 digits", null);
        }

        CorporateClient corporateClient = corporateClientRepository.findById(corporateClientId)
                .orElseThrow(() -> new ResourceNotFound("Corporate account not found with ID: " + corporateClientId));

        String hashedPIN = passwordEncoder.encode(pin);
        corporateClient.setPinHash(hashedPIN);
        corporateClient.setPinSet(true);
        corporateClient.setPinEnabled(true);

        corporateClientRepository.save(corporateClient);

        log.info("PIN set for corporate account | corporateClientId={}", corporateClientId);
        return responseUtil.success("PIN set successfully", null);
    }

    public boolean validateCorporatePin(String corporateClientId, String pin) {
        CorporateClient corporateClient = corporateClientRepository.findById(corporateClientId)
                .orElse(null);

        if (corporateClient == null) {
            log.warn("Corporate account not found for PIN validation | corporateClientId={}", corporateClientId);
            return false;
        }

        if (!corporateClient.isPinSet() || !corporateClient.isPinEnabled()) {
            log.warn("Corporate PIN not set or disabled | corporateClientId={}", corporateClientId);
            return false;
        }

        if (corporateClient.getPinHash() == null || corporateClient.getPinHash().isBlank()) {
            log.warn("Corporate PIN hash is empty | corporateClientId={}", corporateClientId);
            return false;
        }

        return passwordEncoder.matches(pin, corporateClient.getPinHash());
    }


    public void validateCorporateRequest(CreateCorporateClientRequest request, boolean resuming) {
        if (request == null) {
            throw new FunctionException("Please provide the corporate account details to continue.", null);
        }

        if (!StringUtils.hasText(clientGroupId)) {
            throw new IllegalStateException("Corporate client group ID is not configured.");
        }

        insureBvnIsCorrect(request.bvn());

        String email = normalizeEmail(request.email());
        if (!StringUtils.hasText(email)) {
            throw new FunctionException("Please provide a valid corporate email address.", null);
        }

        CreateCorporateClientRequest.ContactPayload contact = resolvePrimaryContact(request);
        if (contact == null || !StringUtils.hasText(contact.email())) {
            throw new FunctionException("Please provide a valid contact email to continue with corporate account creation.", null);
        }

        validateCorporateAccountDoesNotExist(request, contact, email);

        if (!resuming) {
            // todo: comment this out until myWealth resolves it.
//            requireUsernameAvailableOnMyWealth(normalizeUsername(firstNonBlank(contact.username(), contact.email())));
        }
    }


    private void requireUsernameAvailableOnMyWealth(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }

        UsernameAvailabilityResponse availability = externalAPIs.isContactUsernameAvailable(username);

        if (availability.isIndeterminate()) {
            log.warn("Corporate username availability could not be confirmed | username={}", username);
            throw new UpstreamServiceException(
                    "MyWealth",
                    "We could not verify this email right now. Please try again shortly.",
                    null);
        }

        if (!availability.isAvailable()) {
            log.info("Corporate username already taken on MyWealth | username={}", username);
            throw new DuplicateResourceException(
                    "An account with this email address already exists.",
                    "primaryContact.email");
        }
    }

    private void validateCorporateAccountDoesNotExist(
            CreateCorporateClientRequest request,
            CreateCorporateClientRequest.ContactPayload contact,
            String businessEmail
    ) {
        String contactEmail = normalizeEmail(contact.email());
        String username = normalizeUsername(firstNonBlank(contact.username(), contact.email()));
        String bvn = request.bvn().trim();
        String businessMobileNo = normalizePhone(request.mobileNo());
        String contactMobileNo = normalizePhone(contact.mobileNo());

//        if (corporateClientRepository.existsByEmailIgnoreCase(businessEmail)) {
//            throw new DuplicateResourceException("Business email already exists", "email");
//        }

        if (corporateClientRepository.existsByPrimaryContactEmailIgnoreCase(contactEmail)) {
            throw new DuplicateResourceException("Primary contact email already exists", "primaryContact.email");
        }

        if (StringUtils.hasText(username) && corporateClientRepository.existsByPrimaryContactUsernameIgnoreCase(username)) {
            throw new DuplicateResourceException("Username already exists", "primaryContact.username");
        }

        if (corporateClientRepository.existsByBvn(bvn)) {
            throw new DuplicateResourceException("BVN already exists", "bvn");
        }

        if (StringUtils.hasText(businessMobileNo) && corporateClientRepository.existsByMobileNo(businessMobileNo)) {
            throw new DuplicateResourceException("Business mobile number already exists", "mobileNo");
        }

        if (StringUtils.hasText(contactMobileNo) && corporateClientRepository.existsByPrimaryContactMobileNo(contactMobileNo)) {
            throw new DuplicateResourceException("Primary contact mobile number already exists", "primaryContact.mobileNo");
        }
    }

    private void insureBvnIsCorrect(String bvn) {
        if (!StringUtils.hasText(bvn)) {
            throw new FunctionException("Please provide your BVN to continue with corporate account creation.", null);
        }

        String value = bvn.trim();
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new FunctionException("BVN must contain numbers only.", null);
        }

        if (value.length() != BVN_LENGTH) {
            throw new FunctionException("BVN must be exactly 11 digits.", null);
        }
    }

    private CorporateClient saveCorporateClient(
            CreateCorporateClientRequest request,
            CreateCorporateClientRequest.ExternalPayload externalPayload,
            MyWealthClientResponse externalClient
    ) {
        CreateCorporateClientRequest.ContactPayload primaryContact = resolvePrimaryContact(request);
        CreateCorporateClientRequest.ExternalContactPayload externalPrimaryContact = externalPayload.contact().get(0);

        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setExternalClientId(firstNonBlank(externalClient.id(), externalClient.code()));
        corporateClient.setExternalClientCode(externalClient.code());
        corporateClient.setExternalStatus(externalClient.status());
        corporateClient.setClientType(firstNonBlank(externalClient.clientType(), externalPayload.clientType()));
        corporateClient.setBusinessStructure(firstNonBlank(externalClient.businessStructure(), externalPayload.businessStructure()));
        corporateClient.setRefCode(firstNonBlank(externalClient.refCode(), externalPayload.refCode()));
        corporateClient.setLabel(firstNonBlank(externalClient.label(), externalPayload.label()));
        corporateClient.setNotes(externalPayload.notes());
        corporateClient.setEmail(normalizeEmail(firstNonBlank(externalClient.email(), request.email())));
        corporateClient.setNotificationEmail(normalizeEmail(firstNonBlank(externalClient.notificationEmail(), externalPayload.notificationEmail())));
        corporateClient.setMobileNo(normalizePhone(firstNonBlank(externalClient.mobileNo(), request.mobileNo())));
        corporateClient.setValuationCurrency(firstNonBlank(externalClient.valuationCurrency(), externalPayload.valuationCurrency()));
        corporateClient.setGroupId(firstNonBlank(externalClient.groupId(), externalPayload.groupId()));
        corporateClient.setAutoApprove(externalPayload.autoApprove());
        corporateClient.setBusinessIncDate(externalPayload.businessIncDate());
        corporateClient.setNotificationEnabled(firstNonNull(externalClient.notificationEnabled(), externalPayload.notificationEnabled()));
        corporateClient.setPrimaryAddress(formatAddress(request.businessAddress()));

        corporateClient.setPrimaryContactRole(externalPrimaryContact.role());
        corporateClient.setPrimaryContactFirstName(primaryContact.firstName());
        corporateClient.setPrimaryContactMiddleName(primaryContact.middleName());
        corporateClient.setPrimaryContactLastName(primaryContact.lastName());
        corporateClient.setPrimaryContactEmail(normalizeEmail(primaryContact.email()));
        corporateClient.setPrimaryContactMobileNo(normalizePhone(primaryContact.mobileNo()));
        corporateClient.setPrimaryContactUsername(normalizeUsername(externalPrimaryContact.username()));
        corporateClient.setGrantLoginAccess(externalPrimaryContact.grantLoginAccess());

        if (StringUtils.hasText(primaryContact.password())) {
            corporateClient.setPasswordHash(passwordEncoder.encode(primaryContact.password()));
        }

        boolean hasPin = StringUtils.hasText(request.pin());
        corporateClient.setPinHash(hasPin ? passwordEncoder.encode(request.pin()) : null);
        corporateClient.setPinSet(hasPin);
        corporateClient.setPinEnabled(hasPin);
        corporateClient.setBvn(request.bvn().trim());
        corporateClient.setIdType(externalPrimaryContact.idType());
        corporateClient.setIdExpDate(externalPrimaryContact.idExpDate());
        corporateClient.setProfession(externalPrimaryContact.profession());
        corporateClient.setNationality(externalPrimaryContact.nationality());
        corporateClient.setIdNo(externalPrimaryContact.idNo());
        corporateClient.setFinIdNo(externalPrimaryContact.finIdNo());
        corporateClient.setInvestmentObj(externalPrimaryContact.investmentObj());
        corporateClient.setSourceOfWealth(externalPrimaryContact.sourceOfWealth());
        corporateClient.setInvestmentExperience(externalPrimaryContact.investmentExperience());
        corporateClient.setRiskTolerance(externalPrimaryContact.riskTolerance());
        corporateClient.setRiskRating(externalPrimaryContact.riskRating());
        corporateClient.setMoneyLaunderingRisk(externalPrimaryContact.moneyLaunderingRisk());
        corporateClient.setPoliticallyExposed(externalPrimaryContact.politicallyExposed());
        corporateClient.setCriminalConviction(externalPrimaryContact.criminalConviction());
        corporateClient.setEmployerName(primaryContact.employerName());
        corporateClient.setEmployerAddress(objectToString(externalPrimaryContact.employerAddress()));
        corporateClient.setAddressesPayload(toJson(externalPayload.address()));
        corporateClient.setContactsPayload(toStoredJson(externalPayload.contact()));

        return corporateClientRepository.save(corporateClient);
    }

    @Transactional
    public CorporateClient applyPendingOnboardingData(CorporateClient corporateClient, PendingCorporateData pending) {
        if (corporateClient == null || pending == null) {
            return corporateClient;
        }

        corporateClient.setRcNumber(pending.getRcNumber());
        corporateClient.setCompanyName(pending.getCompanyName());
        corporateClient.setCompanyType(pending.getCompanyType());
        corporateClient.setClassification(pending.getClassification());
        corporateClient.setCompanyStatus(pending.getCompanyStatus());
        corporateClient.setCompanyEmail(pending.getCompanyEmail());
        corporateClient.setMemartUrl(pending.getMemartUrl());
        corporateClient.setHeadOfficeAddress(pending.getHeadOfficeAddress());
        corporateClient.setBranchAddress(pending.getBranchAddress());
        corporateClient.setState(pending.getState());
        corporateClient.setCity(pending.getCity());
        corporateClient.setLga(pending.getLga());
        corporateClient.setPostCode(pending.getPostCode());
        corporateClient.setCountry(pending.getCountry());
        corporateClient.setShareCapital(pending.getShareCapital());
        corporateClient.setShareCapitalInWords(pending.getShareCapitalInWords());
        corporateClient.setNumberOfAffiliates(pending.getNumberOfAffiliates());
        corporateClient.setCacVerified(pending.isCacVerified());

        if (StringUtils.hasText(pending.getCompanyName()) && !StringUtils.hasText(corporateClient.getLabel())) {
            corporateClient.setLabel(pending.getCompanyName());
        }

        CorporateClient saved = corporateClientRepository.save(corporateClient);
        persistDirectors(saved, pending);
        return saved;
    }

    private void persistDirectors(CorporateClient corporateClient, PendingCorporateData pending) {
        if (pending.getDirectors() == null || pending.getDirectors().isEmpty()) {
            log.warn("Corporate account created with no directors to record | corporateClientId={}",
                    corporateClient.getId());
            return;
        }

        // Completion can be retried after a rollback; don't duplicate what a previous attempt wrote.
        if (corporateDirectorRepository.existsByCorporateClientId(corporateClient.getId())) {
            log.info("Directors already recorded for this corporate account | corporateClientId={}",
                    corporateClient.getId());
            return;
        }

        List<CorporateDirector> directors = pending.getDirectors().stream()
                .filter(Objects::nonNull)
                .map(staged -> CorporateDirector.from(staged, corporateClient))
                .toList();

        corporateDirectorRepository.saveAll(directors);
        log.info("Directors recorded | corporateClientId={} | count={}",
                corporateClient.getId(), directors.size());
    }

    private int saveCorporateNextOfKinDetails(CorporateClient corporateClient, CreateCorporateClientRequest request) {
        if (request.nextOfKins() == null || request.nextOfKins().isEmpty()) {
            return 0;
        }

        List<CreateCorporateClientRequest.CorporateNextOfKinPayload> supplied = request.nextOfKins().stream()
                .filter(Objects::nonNull)
                .toList();

        if (supplied.isEmpty()) {
            return 0;
        }
        int primaryIndex = 0;
        for (int i = 0; i < supplied.size(); i++) {
            if (Boolean.TRUE.equals(supplied.get(i).isPrimary())) {
                primaryIndex = i;
                break;
            }
        }

        List<CorporateNextOfKin> nextOfKins = new ArrayList<>();
        for (int i = 0; i < supplied.size(); i++) {
            CreateCorporateClientRequest.CorporateNextOfKinPayload item = supplied.get(i);

            CorporateNextOfKin nextOfKin = new CorporateNextOfKin();
            nextOfKin.setCorporateClient(corporateClient);
            nextOfKin.setFirstName(trimToNull(item.firstName()));
            nextOfKin.setLastName(trimToNull(item.lastName()));
            nextOfKin.setRelationship(uppercaseTrimToNull(item.relationship()));
            nextOfKin.setEmail(normalizeEmail(item.email()));
            nextOfKin.setMobileNo(trimToNull(item.mobileNo()));
            nextOfKin.setAddress(trimToNull(item.address()));
            nextOfKin.setPrimary(i == primaryIndex);

            nextOfKins.add(nextOfKin);
        }

        corporateNextOfKinRepository.saveAll(nextOfKins);
        return nextOfKins.size();
    }

    private CreateCorporateClientRequest.ContactPayload resolvePrimaryContact(CreateCorporateClientRequest request) {
        return request == null ? null : request.primaryContact();
    }

    private String formatAddress(CreateCorporateClientRequest.AddressPayload address) {
        if (address == null) {
            return null;
        }
        return joinAddressParts(
                address.addressLine1(),
                address.addressLine2(),
                address.city(),
                address.state(),
                address.postCode(),
                address.country()
        );
    }

    private String formatAddress(CreateCorporateClientRequest.ContactAddressPayload address) {
        if (address == null) {
            return null;
        }
        return joinAddressParts(
                address.addressLine1(),
                address.addressLine2(),
                address.city(),
                address.state(),
                address.postCode(),
                address.country()
        );
    }

    private String joinAddressParts(String... parts) {
        return Arrays.stream(parts)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(", "));
    }

    private String fullName(String firstName, String middleName, String lastName, String fallback) {
        String name = Arrays.stream(new String[]{firstName, middleName, lastName})
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return firstNonBlank(name, fallback, "Related contact");
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return validatorUitls.maskSensitiveFields(objectMapper.writeValueAsString(value));
        } catch (Exception e) {
            return null;
        }
    }

    private String toStoredJson(Object value) {
        if (value == null) {
            return null;
        }

        try {
            JsonNode node = objectMapper.valueToTree(value);
            stripCredentials(node);
            return validatorUitls.maskSensitiveFields(objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            log.warn("Could not serialise payload for storage; storing nothing rather than risking a leak", e);
            return null;
        }
    }

    private void stripCredentials(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove(STORED_PAYLOAD_EXCLUDED_FIELDS);
            objectNode.fields().forEachRemaining(entry -> stripCredentials(entry.getValue()));
        } else if (node != null && node.isArray()) {
            node.forEach(this::stripCredentials);
        }
    }

    private String normalizePhone(String phoneNumber) {
        if (!StringUtils.hasText(phoneNumber)) {
            return null;
        }

        String input = phoneNumber.trim();
        String normalized = input.startsWith("+")
                ? "+" + input.substring(1).replaceAll("[^0-9]", "")
                : input.replaceAll("[^0-9]", "");

        String digitsOnly = normalized.startsWith("+") ? normalized.substring(1) : normalized;
        if (digitsOnly.length() < 10 || digitsOnly.length() > 15) {
            return null;
        }
        return normalized;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String uppercaseTrimToNull(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    private String objectToString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String maskBvn(String bvn) {
        if (bvn == null || bvn.length() < 4) {
            return "****";
        }
        return bvn.substring(0, 2) + "****" + bvn.substring(bvn.length() - 2);
    }

    private boolean isCorporateClientDuplicateConstraint(DataIntegrityViolationException e) {
        String message = databaseErrorMessage(e).toLowerCase(Locale.ROOT);
        return CORPORATE_CLIENT_UNIQUE_CONSTRAINTS.stream().anyMatch(message::contains);
    }

    private String databaseErrorMessage(DataIntegrityViolationException e) {
        Throwable mostSpecificCause = e.getMostSpecificCause();
        if (mostSpecificCause.getMessage() != null) {
            return mostSpecificCause.getMessage();
        }
        return e.getMessage() == null ? "" : e.getMessage();
    }

    private List<NextOfKinRequest> mapCorporateNextOfKins(
            List<CreateCorporateClientRequest.CorporateNextOfKinPayload> nextOfKins) {

        if (nextOfKins == null || nextOfKins.isEmpty()) {
            return Collections.emptyList();
        }

        return nextOfKins.stream()
                .map(nok -> NextOfKinRequest.builder()
                        .fullName(
                                String.join(" ",
                                        Stream.of(
                                                        nok.firstName(),
                                                        nok.middleName(),
                                                        nok.lastName()
                                                )
                                                .filter(Objects::nonNull)
                                                .filter(s -> !s.isBlank())
                                                .toArray(String[]::new)
                                )
                        )
                        .relationship(nok.relationship())
                        .phoneNumber(nok.mobileNo())
                        .email(nok.email())
                        .address(nok.address())
                        .isPrimary(Boolean.TRUE.equals(nok.isPrimary()))
                        .build())
                .toList();
    }


    public ResponseEntity<ApiResponse> authenticateCorporateClient(UserLogin userLogin, CorporateClient corporateClient) {
        if (corporateClient.getPasswordHash() == null || corporateClient.getPasswordHash().isBlank()
                || !passwordEncoder.matches(userLogin.password(), corporateClient.getPasswordHash())) {
            log.warn("Corporate login failed: incorrect password | email={}", corporateClient.getEmail());
            return responseUtil.badRequest("The credentials provided are invalid. Please verify your details and try again.", null);
        }

        if (Boolean.FALSE.equals(corporateClient.getGrantLoginAccess())) {
            log.warn("Corporate login rejected: login access disabled | email={}", corporateClient.getEmail());
            return responseUtil.unauthorized("Login access is disabled for this corporate account.", null);
        }

        boolean changed = false;
        if (corporateClient.getRole() == null) {
            corporateClient.setRole(Role.CORPORATE_ACCOUNT);
            changed = true;
        }
        if (corporateClient.getRole() != Role.CORPORATE_ACCOUNT) {
            return responseUtil.unauthorized("Corporate account role is invalid.", null);
        }
        if (corporateClient.getTier() == null) {
            corporateClient.setTier(3);
            changed = true;
        }
        if (changed) {
            corporateClient = corporateClientRepository.save(corporateClient);
        }

        String accessToken = jwtUtil.generateToken(corporateClient);

        Map<String, Object> data = new HashMap<>();
        data.put("user", corporateClient);
        data.put("isBvnVerified", corporateClient.getBvn() != null && !corporateClient.getBvn().isBlank());
        data.put("isNinVerified", corporateClient.getFinIdNo() != null && !corporateClient.getFinIdNo().isBlank());
        data.put("kycCompleted", corporateClient.getTier() != null && corporateClient.getTier() >= 3);
        data.put("myWealthData", null);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshTokenService.issueForCorporateClient(corporateClient));
        data.put("id", corporateClient.getId());
        data.put("email", corporateClient.getEmail());
        data.put("role", corporateClient.getRole().name());
        data.put("tier", corporateClient.getTier());
        data.put("accountType", AccountType.CORPORATE_ACCOUNT.name());

        log.info("Corporate login successful | email={} | corporateClientId={}", corporateClient.getEmail(), corporateClient.getId());
        return responseUtil.success("Login Successful", data);
    }

    public ResponseEntity<ApiResponse> loginCorporateAccount(UserLogin userLogin) {
        log.info("Corporate login attempt | email={}", userLogin.emailAddress());

        String email = userLogin.emailAddress();
        if (email == null || email.isBlank()) {
            return responseUtil.badRequest("Email is required.", null);
        }

        if (!validatorUitls.isEmail(email)) {
            return responseUtil.badRequest("A valid corporate email address is required.", null);
        }

        email = normalizeEmail(email);
        CorporateClient corporateClient = corporateClientRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (corporateClient == null) {
            return responseUtil.notFound("No corporate account was found with the provided email address.", null);
        }

        return authenticateCorporateClient(userLogin, corporateClient);
    }

    public ResponseEntity<ApiResponse> lookupCorporateAccount(CorporateLookupRequest request) {
        String rcNumber = request.rcNumber() == null ? null : request.rcNumber().trim();
        String bvn = request.bvn() == null ? null : request.bvn().trim();

        Optional<CorporateClient> match = corporateClientRepository.findByRcNumberIgnoreCase(rcNumber)
                .filter(client -> StringUtils.hasText(client.getBvn()) && client.getBvn().trim().equals(bvn));

        if (match.isEmpty()) {
            // Deliberately identical response whether the RC number is unknown or the BVN is wrong,
            // so the endpoint cannot be used to enumerate companies.
            log.warn("Corporate lookup failed | rcNumber={}", rcNumber);
            return responseUtil.notFound("No corporate account matches that RC number and BVN.", null);
        }

        CorporateClient corporateClient = match.get();
        long existingUsers = userRepository.countActiveByCorporateClientId(corporateClient.getId());

        CorporateLookupResponse response = new CorporateLookupResponse(
                validatorUitls.firstNonBlank(corporateClient.getCompanyName(), corporateClient.getLabel()),
                corporateClient.getRcNumber(),
                corporateClient.getRefCode(),
                maskEmail(corporateClient.getEmail()),
                (int) existingUsers,
                MAX_CORPORATE_USERS,
                jwtUtil.generateCorporateAttachToken(corporateClient),
                jwtUtil.corporateAttachTokenTtlSeconds()
        );

        log.info("Corporate lookup succeeded | corporateClientId={} | existingUsers={}",
                corporateClient.getId(), existingUsers);

        return responseUtil.success("Corporate account found", response);
    }


    @Transactional
    public ResponseEntity<ApiResponse> createCorporateUserMain(CreateCorporateUserRequest request) {

        String corporateClientId;
        try {
            corporateClientId = jwtUtil.extractCorporateAttachClientId(request.lookupToken());
        } catch (Exception e) {
            log.warn("Corporate user creation rejected: bad lookup token | reason={}", e.getMessage());
            return responseUtil.unauthorized(
                    "Invalid or expired lookup token. Look the corporate account up again to continue.", null);
        }

        CorporateClient corporateClient = corporateClientRepository.findById(corporateClientId)
                .orElseThrow(() -> new ResourceNotFound("Corporate Account Not Found"));

        if (StringUtils.hasText(request.refCode())
                && !request.refCode().trim().equalsIgnoreCase(corporateClient.getRefCode())) {
            return responseUtil.badRequest("Ref code does not match the corporate account on the lookup token", null);
        }
        if (StringUtils.hasText(request.rcNumber())
                && !request.rcNumber().trim().equalsIgnoreCase(corporateClient.getRcNumber())) {
            return responseUtil.badRequest("RC number does not match the corporate account on the lookup token", null);
        }

        if (!StringUtils.hasText(corporateClient.getExternalClientId())
                || !StringUtils.hasText(corporateClient.getInvestmentId())) {
            log.error("Corporate account is not fully provisioned | corporateClientId={} | externalClientId={} | investmentId={}",
                    corporateClient.getId(), corporateClient.getExternalClientId(), corporateClient.getInvestmentId());
            return responseUtil.conflict(
                    "This corporate account is not fully set up yet. Please email " + CorporateUserPolicy.SUPPORT_EMAIL + " for assistance.",
                    null);
        }

        long existingUsers = userRepository.countActiveByCorporateClientId(corporateClient.getId());
        if (existingUsers >= MAX_CORPORATE_USERS) {
            log.warn("Corporate user cap reached | corporateClientId={} | existingUsers={}",
                    corporateClient.getId(), existingUsers);
            return responseUtil.conflict(
                    "This corporate account already has the maximum of " + MAX_CORPORATE_USERS
                            + " logins. Please email " + CorporateUserPolicy.SUPPORT_EMAIL + " to make changes.",
                    null);
        }

        String email = normalizeEmail(request.emailAddress());

        Optional<ResponseEntity<ApiResponse>> otpVerificationError =
                verifyEmailOtp(email, request.otp(), request.otpReference());
        if (otpVerificationError.isPresent()) {
            return otpVerificationError.get();
        }

        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setMiddleName(request.middleName());
        user.setEmailAddress(email);
        user.setPhoneNumber(request.phoneNumber());
        // Raw password - persistUser() encodes it during creation.
        user.setPassword(request.password());
        user.setBvn(request.bvn());
        user.setAddress(request.address());
        user.setGender(request.gender());
        user.setPep(request.isPep());
        user.setPepPosition(request.pepPosition());
        user.setPin(passwordEncoder.encode(request.pin()));
        user.setUsePin(true);
        user.setCorporateClient(corporateClient);

        QoreIdBvnRequest bvnRequest = new QoreIdBvnRequest(
                request.bvn(), request.firstName(), request.lastName(), request.phoneNumber());
        QoreIdBVNResponse qoreIdBvnResponse = externalAPIs.verifyBvnPremium(bvnRequest);
        if (qoreIdBvnResponse == null || qoreIdBvnResponse.getBvn() == null) {
            log.warn("Corporate user BVN verification failed | email={}", email);
            return responseUtil.serviceUnavailable("Unable to verify BVN at the moment. Please try again later.", null);
        }
        bvnCheckService.bvnValidationAlgorithm(qoreIdBvnResponse, user);
        authService.cacheBvn(qoreIdBvnResponse.getBvn(), request.bvn());

        User savedUser = accountService.createLocalCorporateUser(
                user,
                request.nextOfKins(),
                corporateClient.getExternalClientId(),
                corporateClient.getInvestmentId());

        log.info("Corporate user attached | corporateClientId={} | email={} | totalUsers={}",
                corporateClient.getId(), savedUser.getEmailAddress(), existingUsers + 1);

        TokenResponse tokenResponse = new TokenResponse(
                jwtUtil.generateToken(savedUser),
                refreshTokenService.issueForUser(savedUser)
        );

        return responseUtil.created("Corporate User created successfully", tokenResponse);
    }

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }


    public ResponseEntity<ApiResponse> getPendingCorporateData() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Pending corporate data retrieved successfully",
                CorporateOnboardingPreviewResponse.from(pendingCorporateData));
    }

    /**
     * The read side of the onboarding form. Each getter below answers "what does the server
     * currently hold for this page", so the app can pre-fill a step the user is returning to
     * without replaying the whole flow. All of them are scoped by the onboarding token, so a caller
     * only ever reads its own staged record.
     */

    // Page 1 - basic details
    public ResponseEntity<ApiResponse> getBasicDetails() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Basic details retrieved successfully",
                CorporateBasicDetailsResponse.from(pendingCorporateData));
    }

    // Page 2 - BVN
    public ResponseEntity<ApiResponse> getBvnDetails() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("BVN details retrieved successfully",
                CorporateBvnDetailsResponse.from(pendingCorporateData));
    }

    // Page 4 - KYB
    public ResponseEntity<ApiResponse> getKybDetails() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Business details retrieved successfully",
                CorporateKybDetailsResponse.from(pendingCorporateData));
    }

    // Page 5 - MEMART
    public ResponseEntity<ApiResponse> getMemartFile() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Memart file retrieved successfully",
                CorporateMemartResponse.from(pendingCorporateData));
    }

    // Page 6 - directors
    public ResponseEntity<ApiResponse> getDirectors() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Directors retrieved successfully",
                CorporateDirectorsResponse.from(pendingCorporateData));
    }

    // Page 7 - credentials. PIN and password are reported as set/not set, never returned.
    public ResponseEntity<ApiResponse> getCorporateCredentials() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Corporate credentials retrieved successfully",
                CorporateCredentialsDetailsResponse.from(pendingCorporateData));
    }

    /**
     * Every page in one response, plus the resume page and outstanding steps - one call instead of
     * six for a review screen or for restoring an abandoned session.
     */
    public ResponseEntity<ApiResponse> getFullSubmission() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();
        return responseUtil.success("Corporate onboarding submission retrieved successfully",
                CorporateOnboardingSubmissionResponse.from(
                        pendingCorporateData, determineNextOnboardingPage(pendingCorporateData)));
    }

    // Step - 1: Collect name/phone/gender. Requires the onboarding token issued by /validate-otp.
    public ResponseEntity<ApiResponse> collectBasicDetails(BasicDetails basicDetails) {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        if (corporateClientRepository.findByEmailIgnoreCase(pendingCorporateData.getEmail()).isPresent()) {
            return responseUtil.conflict("Corporate account already exists", null);
        }

        if (StringUtils.hasText(pendingCorporateData.getExternalClientId())) {
            log.warn("Rejected basic-details for an onboarding already created on MyWealth | email={}",
                    pendingCorporateData.getEmail());
            return responseUtil.conflict(
                    "This corporate onboarding has already been submitted. Please continue where you left off,"
                            + " or contact " + CorporateUserPolicy.SUPPORT_EMAIL + " for help.", null);
        }

        // todo: uncomment when myWealth fix the lookup endpoint
//        UsernameAvailabilityResponse availability = externalAPIs.isContactUsernameAvailable(pendingCorporateData.getEmail());
//        if (availability.isIndeterminate()) {
//            log.warn("Corporate username availability could not be confirmed | email={}", pendingCorporateData.getEmail());
//            return responseUtil.serviceUnavailable(
//                    "We could not verify this email right now. Please try again shortly.", null);
//        }
//        if (!availability.isAvailable()) {
//            log.info("Corporate onboarding blocked: username taken on MyWealth | email={}", pendingCorporateData.getEmail());
//            return responseUtil.conflict("An account with this email address already exists.", null);
//        }

        pendingCorporateData.setFirstName(basicDetails.getFirstName());
        pendingCorporateData.setMiddleName(basicDetails.getMiddleName());
        pendingCorporateData.setLastName(basicDetails.getLastName());
        pendingCorporateData.setPhoneNumber(basicDetails.getPhoneNumber());
        pendingCorporateData.setGender(basicDetails.getGender());
        pendingCorporateData.setInviteCode(basicDetails.getInviteCode());
        pendingCorporateData.setCountry("NG");
        pendingCorporateData.setValuationCurrency("NGN");
        pendingCorporateData.setNationality("Nigerian");

        Optional<ResponseEntity<ApiResponse>> duplicate = rejectIfPrimaryContactAlreadyRegistered(pendingCorporateData);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }

        pendingCorporateDataRepository.save(pendingCorporateData);
        trackCorporateProgress(pendingCorporateData);

        return responseUtil.created("Basic details collected successfully", null);
    }

    // Step - 2: Validate BVN against the name/phone collected at basic-details. Requires the token.
    public ResponseEntity<ApiResponse> insureBvnIsCorrect(ValidateBvnRequest request) {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        if (pendingCorporateData.getGender() == null || !StringUtils.hasText(pendingCorporateData.getFirstName())) {
            log.warn("Corporate BVN validation blocked: staged record has no basic details | email={}",
                    pendingCorporateData.getEmail());
            return responseUtil.badRequest(
                    "Your onboarding details are incomplete. Please submit your basic details again to continue.",
                    null);
        }

        Optional<ResponseEntity<ApiResponse>> duplicateBvn = rejectIfBvnAlreadyRegistered(request.bvn());
        if (duplicateBvn.isPresent()) {
            return duplicateBvn.get();
        }

        String bvn = request.bvn();
        BVNRequest bvnRequest = new BVNRequest(
                bvn,
                pendingCorporateData.getFirstName(),
                pendingCorporateData.getLastName(),
                pendingCorporateData.getPhoneNumber(),
                pendingCorporateData.getEmail(),
                pendingCorporateData.getGender().name(),
                true
        );

        try {
            QoreIdBvnRequest qoreIdBvnRequest = new QoreIdBvnRequest(
                    bvn,
                    pendingCorporateData.getFirstName(),
                    pendingCorporateData.getLastName(),
                    pendingCorporateData.getPhoneNumber()
            );
            QoreIdBVNResponse qoreIdBvnResponse = externalAPIs.verifyBvnPremium(qoreIdBvnRequest);

            if (qoreIdBvnResponse == null) {
                log.warn("BVN verification failed | response is null");
                return responseUtil.serviceUnavailable("Unable to verify BVN at the moment. Please try again later.", null);
            }
            bvnCheckService.bvnValidationAlgorithm(qoreIdBvnResponse, bvnRequest);
            BvnValidationResponse bvnValidationResponse = new BvnValidationResponse(qoreIdBvnResponse.getBvn().getMiddlename(), true);

            if (qoreIdBvnResponse.getBvn() != null) {
                authService.cacheBvn(qoreIdBvnResponse.getBvn(), bvn);
                String birthDate = PendingCorporateOnboardingMapper.normalizeBirthDate(qoreIdBvnResponse.getBvn().getBirthdate());
                if (StringUtils.hasText(birthDate)) {
                    pendingCorporateData.setDate(birthDate);
                }
                if (StringUtils.hasText(qoreIdBvnResponse.getBvn().getMaritalStatus())) {
                    pendingCorporateData.setMaritalStatus(qoreIdBvnResponse.getBvn().getMaritalStatus());
                }
            }

            pendingCorporateData.setBvn(bvn);
            pendingCorporateDataRepository.save(pendingCorporateData);
            trackCorporateProgress(pendingCorporateData);

            CorporateBvnValidationResponse response = new CorporateBvnValidationResponse(
                    bvnValidationResponse.getMiddleName(),
                    bvnValidationResponse.isVerified()
            );

            return responseUtil.created("BVN verified and collected successfully", response);
        } catch (FunctionException e) {
            throw e;
        } catch (Exception ex) {
            log.error("BVN verification | unexpected error", ex);
            return responseUtil.serverError("An unexpected error occurred while verifying BVN.", ex.getMessage());
        }
    }

    private Optional<ResponseEntity<ApiResponse>> verifyEmailOtp(String email, String otp, String otpReference) {
        if (otp == null || otp.isBlank() || otpReference == null || otpReference.isBlank()) {
            return Optional.of(responseUtil.badRequest("Email OTP and OTP reference are required", null));
        }

        Optional<UserOtp> userOtpOptional = otpRepository.findByOtpReference(otpReference);
        if (userOtpOptional.isEmpty()) {
            return Optional.of(responseUtil.notFound("OTP not found for this reference", null));
        }

        UserOtp userOtp = userOtpOptional.get();

        // The reference alone is not proof - it must have been issued to this exact email,
        // otherwise any valid reference could authorise any address.
        if (email == null || !normalizeEmail(email).equals(normalizeEmail(userOtp.getEmail()))) {
            log.warn("OTP reference does not belong to the supplied email | reference={}", otpReference);
            return Optional.of(responseUtil.badRequest("OTP does not match this email address", null));
        }

        if (userOtp.isVerified()) {
            return Optional.of(responseUtil.badRequest("OTP already verified", null));
        }
        if (userOtp.isExpired()) {
            return Optional.of(responseUtil.badRequest("OTP expired. generate an OTP to continue", null));
        }
        if (!passwordEncoder.matches(otp, userOtp.getOtp())) {
            return Optional.of(responseUtil.badRequest("Invalid OTP", null));
        }

        userOtp.markVerified();
        otpRepository.save(userOtp);
        return Optional.empty();
    }

    // Step - 3: KYB — verify CAC/RC number and collect company details
    public ResponseEntity<ApiResponse> verifyAndCollectKYB(BusinessInformation businessInformation) {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        try {
            QoreIdCacRequest qoreIdCacRequest = new QoreIdCacRequest(businessInformation.getRcNumber());
            QoreIdCacResponse qoreIdCacResponse = externalAPIs.verifyCacPremium(qoreIdCacRequest);

            if (qoreIdCacResponse == null) {
                log.warn("Business Number verification failed | response is null");
                return responseUtil.serviceUnavailable("Unable to verify Business Number at the moment. Please try again later.", null);
            }

            mapCacResponseToPendingCorporateData(pendingCorporateData, qoreIdCacResponse);
            pendingCorporateData.setRcNumber(validatorUitls.firstNonBlank(
                    getStringValue(qoreIdCacResponse.getCac(), "rcNumber"),
                    businessInformation.getRcNumber()
            ));
            pendingCorporateDataRepository.save(pendingCorporateData);

            Optional<ResponseEntity<ApiResponse>> incomplete = rejectIfKybDataIncomplete(pendingCorporateData);
            if (incomplete.isPresent()) {
                return incomplete.get();
            }

            trackCorporateProgress(pendingCorporateData);
            return responseUtil.created("Business details verified and collected successfully", null);
        } catch (FunctionException e) {
            throw e;
        } catch (Exception ex) {
            log.error("Business Number verification | unexpected error", ex);
            return responseUtil.serverError("An unexpected error occurred while verifying Business Number.", ex.getMessage());
        }
    }

    // Step - 4: Collect MEMART document URL
    public ResponseEntity<ApiResponse> collectMemartFile(MemartFileRequest memartFileRequest) {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        pendingCorporateData.setMemartUrl(memartFileRequest.url());
        pendingCorporateDataRepository.save(pendingCorporateData);
        trackCorporateProgress(pendingCorporateData);

        return responseUtil.success("Memart file collected successfully", null);
    }

    // Step - 5: Collect directors (and beneficial owners where flagged)
    @Transactional
    public ResponseEntity<ApiResponse> collectDirectors(List<DirectorPayload> directors) {
        if (directors == null || directors.isEmpty()) {
            return responseUtil.badRequest("At least one director is required", null);
        }

        Optional<ResponseEntity<ApiResponse>> bvnError = validateDirectorBvns(directors);
        if (bvnError.isPresent()) {
            return bvnError.get();
        }

        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        List<PendingCorporateDirector> directorEntities = directors.stream()
                .map(this::toPendingCorporateDirector)
                .toList();

        pendingCorporateData.replaceDirectors(directorEntities);
        pendingCorporateDirectorRepository.saveAll(pendingCorporateData.getDirectors());
        trackCorporateProgress(pendingCorporateData);

        return responseUtil.success("Directors collected successfully", null);
    }

    // Step - 6: Collect credentials and remaining MyWealth-mandatory fields. Returns the full staged
    // record as a preview so the frontend can show a review screen before /corporate/complete.
    public ResponseEntity<ApiResponse> collectCorporateCredentials(CorporateCredentialsRequest request) {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        Optional<ResponseEntity<ApiResponse>> invalid = rejectInvalidCredentialFields(request);
        if (invalid.isPresent()) {
            return invalid.get();
        }

        pendingCorporateData.setPin(request.pin());
        pendingCorporateData.setPassword(request.password());
        pendingCorporateData.setProfession(request.profession());
        pendingCorporateData.setNationality(request.nationality());
        pendingCorporateData.setIdExpiry(request.idExpDate());

        if (StringUtils.hasText(request.valuationCurrency())) {
            pendingCorporateData.setValuationCurrency(request.valuationCurrency());
        }
        if (StringUtils.hasText(request.businessStructure())) {
            pendingCorporateData.setBusinessStructure(request.businessStructure());
        }
        if (StringUtils.hasText(request.username())) {
            pendingCorporateData.setUsername(request.username());
        }
        if (StringUtils.hasText(request.title())) {
            pendingCorporateData.setTitle(request.title());
        }
        if (StringUtils.hasText(request.maritalStatus())) {
            pendingCorporateData.setMaritalStatus(request.maritalStatus());
        }
        if (StringUtils.hasText(request.employerName())) {
            pendingCorporateData.setEmployerName(request.employerName());
        }
        if (StringUtils.hasText(request.employerAddress())) {
            pendingCorporateData.setEmployerAddress(request.employerAddress());
        }
        if (StringUtils.hasText(request.netWorth())) {
            pendingCorporateData.setNetWorth(request.netWorth());
        }
        if (StringUtils.hasText(request.annualIncome())) {
            pendingCorporateData.setAnnualIncome(request.annualIncome());
        }
        if (StringUtils.hasText(request.liquidNetWorth())) {
            pendingCorporateData.setLiquidNetWorth(request.liquidNetWorth());
        }
        if (StringUtils.hasText(request.sourceOfWealth())) {
            pendingCorporateData.setSourceOfWealth(request.sourceOfWealth());
        }
        if (StringUtils.hasText(request.investmentExperience())) {
            pendingCorporateData.setInvestmentExperience(request.investmentExperience());
        }
        if (StringUtils.hasText(request.riskTolerance())) {
            pendingCorporateData.setRiskTolerance(request.riskTolerance());
        }
        if (StringUtils.hasText(request.investmentObjective())) {
            pendingCorporateData.setInvestmentObjective(request.investmentObjective());
        }

        pendingCorporateDataRepository.save(pendingCorporateData);
        trackCorporateProgress(pendingCorporateData);
        return responseUtil.success("Corporate credentials collected successfully",
                CorporateOnboardingProgressResponse.from(pendingCorporateData));
    }


    private Optional<ResponseEntity<ApiResponse>> rejectIfAlreadySubmitted(PendingCorporateData pending) {
        if (StringUtils.hasText(pending.getExternalClientId())) {
            log.warn("Rejected corporate onboarding edit: already created on MyWealth | email={}", pending.getEmail());
            return Optional.of(responseUtil.conflict(
                    "This corporate onboarding has already been submitted and can no longer be edited. Please contact "
                            + CorporateUserPolicy.SUPPORT_EMAIL + " for help.", null));
        }
        return Optional.empty();
    }

    // ==================================================================================
    // Early completion validators. /complete would otherwise be the first place several of these
    // failures surface - after the user has filled the whole form. Each step runs the checks for
    // the data it collects, so the error is returned as soon as the offending value is submitted
    // and /complete is guaranteed to pass. Shared by the POST and PATCH variants of each step.
    // ==================================================================================

    // Duplicate checks that /complete performs against the primary contact (business email is
    // deliberately excluded - that constraint was relaxed). The primary contact email and mobile
    // are known from basic-details onward.
    private Optional<ResponseEntity<ApiResponse>> rejectIfPrimaryContactAlreadyRegistered(PendingCorporateData pending) {
        String email = normalizeEmail(pending.getEmail());
        if (StringUtils.hasText(email) && corporateClientRepository.existsByPrimaryContactEmailIgnoreCase(email)) {
            return Optional.of(responseUtil.conflict("Primary contact email already exists",
                    Map.of("field", "primaryContact.email")));
        }

        String mobile = normalizePhone(pending.getPhoneNumber());
        if (StringUtils.hasText(mobile)
                && (corporateClientRepository.existsByMobileNo(mobile)
                || corporateClientRepository.existsByPrimaryContactMobileNo(mobile))) {
            return Optional.of(responseUtil.conflict("Mobile number already exists",
                    Map.of("field", "mobileNo")));
        }
        return Optional.empty();
    }

    // /complete rejects a BVN already registered to another corporate account. The BVN is known at
    // the validate-bvn step, so reject there - before spending a paid QoreID verification on it.
    private Optional<ResponseEntity<ApiResponse>> rejectIfBvnAlreadyRegistered(String bvn) {
        if (StringUtils.hasText(bvn) && corporateClientRepository.existsByBvn(bvn.trim())) {
            return Optional.of(responseUtil.conflict("BVN already exists", Map.of("field", "bvn")));
        }
        return Optional.empty();
    }

    // The MyWealth mapping requires a company name, an incorporation date and a business address
    // (line, city, state). These come from the CAC record at KYB; if CAC did not return them the
    // mapping throws at /complete. Surface it at the KYB step instead.
    private Optional<ResponseEntity<ApiResponse>> rejectIfKybDataIncomplete(PendingCorporateData pending) {
        if (!StringUtils.hasText(pending.getCompanyName())) {
            return Optional.of(responseUtil.badRequest(
                    "We could not read the company name from the CAC record. Please confirm the RC number.", null));
        }
        if (pending.getRegistrationDate() == null) {
            return Optional.of(responseUtil.badRequest(
                    "We could not read the company incorporation date from the CAC record. Please confirm the RC number.", null));
        }
        String line1 = firstNonBlank(pending.getHeadOfficeAddress(), pending.getBranchAddress(), pending.getAddress());
        String city = firstNonBlank(pending.getCity(), pending.getLga(), pending.getState());
        String state = firstNonBlank(pending.getState(), pending.getLga());
        if (!StringUtils.hasText(line1) || !StringUtils.hasText(city) || !StringUtils.hasText(state)) {
            return Optional.of(responseUtil.badRequest(
                    "We could not read a complete business address from the CAC record. Please confirm the RC number"
                            + " or contact " + CorporateUserPolicy.SUPPORT_EMAIL + ".", null));
        }
        return Optional.empty();
    }

    // The financial-profile enums and a custom username are validated at /complete via the MyWealth
    // mapping. Validate them at the credentials step so a bad value is rejected on submission.
    private Optional<ResponseEntity<ApiResponse>> rejectInvalidCredentialFields(CorporateCredentialsRequest request) {
        // Enforced here (not just via @Valid) so the PATCH path - which accepts partial payloads and
        // is not bean-validated - rejects a bad PIN or password the same way the POST path does.
        if (StringUtils.hasText(request.pin()) && !request.pin().trim().matches("^\\d{4}$")) {
            return Optional.of(responseUtil.badRequest("PIN must be exactly 4 digits", Map.of("field", "pin")));
        }
        if (StringUtils.hasText(request.password()) && request.password().length() < 8) {
            return Optional.of(responseUtil.badRequest("Password must be at least 8 characters",
                    Map.of("field", "password")));
        }

        try {
            CreateCorporateClientRequest.validateProfileFields(
                    request.businessStructure(),
                    request.title(),
                    request.sourceOfWealth(),
                    request.investmentExperience(),
                    request.riskTolerance(),
                    request.investmentObjective(),
                    request.netWorth(),
                    request.annualIncome(),
                    request.liquidNetWorth()
            );
        } catch (IllegalArgumentException e) {
            return Optional.of(responseUtil.badRequest(e.getMessage(), null));
        }

        if (StringUtils.hasText(request.username())) {
            String username = normalizeUsername(request.username());
            if (StringUtils.hasText(username)
                    && corporateClientRepository.existsByPrimaryContactUsernameIgnoreCase(username)) {
                return Optional.of(responseUtil.conflict("Username already exists",
                        Map.of("field", "primaryContact.username")));
            }
        }
        return Optional.empty();
    }

    // PATCH - page 1: update name/phone/gender. Email is the identity anchor and is not editable.
    public ResponseEntity<ApiResponse> updateBasicDetails(BasicDetails basicDetails) {
        PendingCorporateData pending = requirePendingCorporateData();
        Optional<ResponseEntity<ApiResponse>> blocked = rejectIfAlreadySubmitted(pending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        if (StringUtils.hasText(basicDetails.getFirstName())) {
            pending.setFirstName(basicDetails.getFirstName());
        }
        if (StringUtils.hasText(basicDetails.getMiddleName())) {
            pending.setMiddleName(basicDetails.getMiddleName());
        }
        if (StringUtils.hasText(basicDetails.getLastName())) {
            pending.setLastName(basicDetails.getLastName());
        }
        if (StringUtils.hasText(basicDetails.getPhoneNumber())) {
            pending.setPhoneNumber(basicDetails.getPhoneNumber());
        }
        if (basicDetails.getGender() != null) {
            pending.setGender(basicDetails.getGender());
        }

        Optional<ResponseEntity<ApiResponse>> duplicate = rejectIfPrimaryContactAlreadyRegistered(pending);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }

        pendingCorporateDataRepository.save(pending);
        trackCorporateProgress(pending);
        return responseUtil.success("Basic details updated successfully", null);
    }

    public ResponseEntity<ApiResponse> updateBvn(ValidateBvnRequest request) {
        PendingCorporateData pending = requirePendingCorporateData();
        Optional<ResponseEntity<ApiResponse>> blocked = rejectIfAlreadySubmitted(pending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        if (request == null || !StringUtils.hasText(request.bvn())) {
            return responseUtil.badRequest("BVN is required to update your KYC.", null);
        }
        return insureBvnIsCorrect(request);
    }

    // PATCH - page 4: correct the RC number. Re-verifies the company with QoreID, same as the POST.
    public ResponseEntity<ApiResponse> updateBusinessInformation(BusinessInformation businessInformation) {
        PendingCorporateData pending = requirePendingCorporateData();
        Optional<ResponseEntity<ApiResponse>> blocked = rejectIfAlreadySubmitted(pending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        if (businessInformation == null || !StringUtils.hasText(businessInformation.getRcNumber())) {
            return responseUtil.badRequest("RC number is required to update your business information.", null);
        }
        return verifyAndCollectKYB(businessInformation);
    }

    // PATCH - page 5: replace the MEMART document URL.
    public ResponseEntity<ApiResponse> updateMemart(MemartFileRequest memartFileRequest) {
        PendingCorporateData pending = requirePendingCorporateData();
        Optional<ResponseEntity<ApiResponse>> blocked = rejectIfAlreadySubmitted(pending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        if (memartFileRequest == null || !StringUtils.hasText(memartFileRequest.url())) {
            return responseUtil.badRequest("MEMART document URL is required to update.", null);
        }
        pending.setMemartUrl(memartFileRequest.url());
        pendingCorporateDataRepository.save(pending);
        trackCorporateProgress(pending);
        return responseUtil.success("Memart file updated successfully", null);
    }

    // PATCH - page 6: replace the director list. Existing director rows are removed first so an edit
    // does not accumulate stale records.
    @Transactional
    public ResponseEntity<ApiResponse> updateDirectors(List<DirectorPayload> directors) {
        PendingCorporateData pending = requirePendingCorporateData();
        Optional<ResponseEntity<ApiResponse>> blocked = rejectIfAlreadySubmitted(pending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        if (directors == null || directors.isEmpty()) {
            return responseUtil.badRequest("At least one director is required", null);
        }
        Optional<ResponseEntity<ApiResponse>> bvnError = validateDirectorBvns(directors);
        if (bvnError.isPresent()) {
            return bvnError.get();
        }

        pendingCorporateDirectorRepository.deleteByPendingCorporateDataId(pending.getId());

        List<PendingCorporateDirector> directorEntities = directors.stream()
                .map(this::toPendingCorporateDirector)
                .toList();
        pending.replaceDirectors(directorEntities);
        pendingCorporateDirectorRepository.saveAll(pending.getDirectors());
        trackCorporateProgress(pending);

        return responseUtil.success("Directors updated successfully", null);
    }

    // PATCH - page 7: merge non-null credential and financial-profile fields.
    public ResponseEntity<ApiResponse> updateCorporateCredentials(CorporateCredentialsRequest request) {
        PendingCorporateData pending = requirePendingCorporateData();
        Optional<ResponseEntity<ApiResponse>> blocked = rejectIfAlreadySubmitted(pending);
        if (blocked.isPresent()) {
            return blocked.get();
        }

        Optional<ResponseEntity<ApiResponse>> invalid = rejectInvalidCredentialFields(request);
        if (invalid.isPresent()) {
            return invalid.get();
        }

        if (StringUtils.hasText(request.pin())) {
            pending.setPin(request.pin());
        }
        if (StringUtils.hasText(request.password())) {
            pending.setPassword(request.password());
        }
        if (StringUtils.hasText(request.profession())) {
            pending.setProfession(request.profession());
        }
        if (StringUtils.hasText(request.nationality())) {
            pending.setNationality(request.nationality());
        }
        if (StringUtils.hasText(request.idExpDate())) {
            pending.setIdExpiry(request.idExpDate());
        }
        if (StringUtils.hasText(request.valuationCurrency())) {
            pending.setValuationCurrency(request.valuationCurrency());
        }
        if (StringUtils.hasText(request.businessStructure())) {
            pending.setBusinessStructure(request.businessStructure());
        }
        if (StringUtils.hasText(request.username())) {
            pending.setUsername(request.username());
        }
        if (StringUtils.hasText(request.title())) {
            pending.setTitle(request.title());
        }
        if (StringUtils.hasText(request.maritalStatus())) {
            pending.setMaritalStatus(request.maritalStatus());
        }
        if (StringUtils.hasText(request.employerName())) {
            pending.setEmployerName(request.employerName());
        }
        if (StringUtils.hasText(request.employerAddress())) {
            pending.setEmployerAddress(request.employerAddress());
        }
        if (StringUtils.hasText(request.netWorth())) {
            pending.setNetWorth(request.netWorth());
        }
        if (StringUtils.hasText(request.annualIncome())) {
            pending.setAnnualIncome(request.annualIncome());
        }
        if (StringUtils.hasText(request.liquidNetWorth())) {
            pending.setLiquidNetWorth(request.liquidNetWorth());
        }
        if (StringUtils.hasText(request.sourceOfWealth())) {
            pending.setSourceOfWealth(request.sourceOfWealth());
        }
        if (StringUtils.hasText(request.investmentExperience())) {
            pending.setInvestmentExperience(request.investmentExperience());
        }
        if (StringUtils.hasText(request.riskTolerance())) {
            pending.setRiskTolerance(request.riskTolerance());
        }
        if (StringUtils.hasText(request.investmentObjective())) {
            pending.setInvestmentObjective(request.investmentObjective());
        }

        pendingCorporateDataRepository.save(pending);
        trackCorporateProgress(pending);
        return responseUtil.success("Corporate credentials updated successfully",
                CorporateOnboardingProgressResponse.from(pending));
    }

    // Step - 7: Create MyWealth corporate client, save locally, and finalize onboarding
    @Transactional
    public ResponseEntity<ApiResponse> completeCorporateOnboarding() {
        PendingCorporateData pendingCorporateData = requirePendingCorporateData();

        if (!pendingCorporateData.isEmailVerified() || !StringUtils.hasText(pendingCorporateData.getBvn())) {
            return responseUtil.badRequest("KYC must be completed before creating the corporate account", null);
        }
        if (!pendingCorporateData.isCacVerified() || !StringUtils.hasText(pendingCorporateData.getRcNumber())) {
            return responseUtil.badRequest("KYB must be completed before creating the corporate account", null);
        }
        if (!StringUtils.hasText(pendingCorporateData.getMemartUrl())) {
            return responseUtil.badRequest("MEMART document must be collected before creating the corporate account", null);
        }
        if (pendingCorporateData.getDirectors() == null || pendingCorporateData.getDirectors().isEmpty()) {
            return responseUtil.badRequest("At least one director must be collected before creating the corporate account", null);
        }
        if (!StringUtils.hasText(pendingCorporateData.getPin()) || !StringUtils.hasText(pendingCorporateData.getPassword())) {
            return responseUtil.badRequest("Corporate credentials must be collected before creating the corporate account", null);
        }

        backfillRepresentativeBirthDateFromBvnCache(pendingCorporateData);
        CreateCorporateClientRequest request = PendingCorporateOnboardingMapper.toCreateCorporateClientRequest(pendingCorporateData);
// todo: I want to see the output of 'request' here ----
        ResponseEntity<ApiResponse> creationResponse = createCorporateAccount(request, pendingCorporateData);
        if (creationResponse.getStatusCode().isError()) {
            return creationResponse;
        }

        CorporateClient corporateClient = corporateClientRepository.findByEmailIgnoreCase(
                validatorUitls.firstNonBlank(pendingCorporateData.getCompanyEmail(), pendingCorporateData.getEmail())
        ).orElseGet(() -> corporateClientRepository.findByEmailIgnoreCase(pendingCorporateData.getEmail())
                .orElseThrow(() -> new ResourceNotFound("Corporate account was created externally but not found locally")));

        applyPendingOnboardingData(corporateClient, pendingCorporateData);

        return creationResponse;
    }

    private void backfillRepresentativeBirthDateFromBvnCache(PendingCorporateData pendingCorporateData) {
        if (StringUtils.hasText(pendingCorporateData.getDate()) || !StringUtils.hasText(pendingCorporateData.getBvn())) {
            return;
        }
        BvnCache cached = bvnCacheRepo.findBvnCacheByBvn(pendingCorporateData.getBvn());
        if (cached == null) {
            return;
        }
        String birthDate = PendingCorporateOnboardingMapper.normalizeBirthDate(cached.getBirthdate());
        if (StringUtils.hasText(birthDate)) {
            pendingCorporateData.setDate(birthDate);
        }
        if (!StringUtils.hasText(pendingCorporateData.getMaritalStatus()) && StringUtils.hasText(cached.getMaritalStatus())) {
            pendingCorporateData.setMaritalStatus(cached.getMaritalStatus());
        }
    }

    private Optional<ResponseEntity<ApiResponse>> validateDirectorBvns(List<DirectorPayload> directors) {
        for (int i = 0; i < directors.size(); i++) {
            DirectorPayload director = directors.get(i);

            if (director == null) {
                return Optional.of(responseUtil.badRequest("Director " + (i + 1) + " is empty.", null));
            }

            String bvn = director.bvn() == null ? "" : director.bvn().trim();
            if (bvn.isEmpty()) {
                return Optional.of(responseUtil.badRequest(
                        "BVN is required for " + describeDirector(director, i) + ".", null));
            }
            if (!bvn.matches("^\\d{11}$")) {
                return Optional.of(responseUtil.badRequest(
                        "BVN for " + describeDirector(director, i) + " must be exactly 11 digits.", null));
            }
        }
        return Optional.empty();
    }

    private String describeDirector(DirectorPayload director, int index) {
        String name = Stream.of(director.firstName(), director.lastName())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.joining(" "));
        return StringUtils.hasText(name) ? name : "director " + (index + 1);
    }

    private PendingCorporateDirector toPendingCorporateDirector(DirectorPayload payload) {
        PendingCorporateDirector director = new PendingCorporateDirector();
        director.setFirstName(payload.firstName());
        director.setMiddleName(payload.middleName());
        director.setLastName(payload.lastName());
        director.setEmail(payload.email());
        director.setPhoneNumber(payload.phoneNumber());
        director.setBvn(payload.bvn());
        director.setRole(StringUtils.hasText(payload.role()) ? payload.role() : "CORP_DIRECTOR");
        director.setBeneficialOwner(Boolean.TRUE.equals(payload.beneficialOwner()));
        director.setGender(payload.gender());
        director.setTitle(payload.title());
        director.setAddressLine1(payload.addressLine1());
        director.setAddressLine2(payload.addressLine2());
        director.setCity(payload.city());
        director.setState(payload.state());
        director.setPostCode(payload.postCode());
        director.setCountry(payload.country());
        director.setBirthDate(payload.birthDate());
        director.setMaritalStatus(payload.maritalStatus());
        return director;
    }

    private void mapCacResponseToPendingCorporateData(PendingCorporateData pendingCorporateData, QoreIdCacResponse response) {
        if (pendingCorporateData == null || response == null) {
            return;
        }

        Map<String, Object> cac = response.getCac();
        if (cac == null || cac.isEmpty()) {
            return;
        }

        pendingCorporateData.setRcNumber(firstNonBlank(
                getStringValue(cac, "rcNumber"),
                pendingCorporateData.getRcNumber()
        ));
        pendingCorporateData.setCompanyName(getStringValue(cac, "companyName"));
        pendingCorporateData.setCompanyType(getStringValue(cac, "companyType"));
        pendingCorporateData.setClassification(getStringValue(cac, "classification"));
        pendingCorporateData.setCompanyStatus(getStringValue(cac, "status"));
        pendingCorporateData.setCompanyEmail(getStringValue(cac, "companyEmail"));
        pendingCorporateData.setHeadOfficeAddress(getStringValue(cac, "headOfficeAddress"));
        pendingCorporateData.setBranchAddress(getStringValue(cac, "branchAddress"));
        pendingCorporateData.setLga(firstNonBlank(
                getStringValue(cac, "lga"),
                getStringValue(cac, "localGovernment")
        ));
        pendingCorporateData.setState(firstNonBlank(
                getStringValue(cac, "state"),
                getStringValue(cac, "companyState")
        ));
        pendingCorporateData.setCity(firstNonBlank(
                getStringValue(cac, "city"),
                getStringValue(cac, "companyCity"),
                getNestedStringValue(cac, "address", "city"),
                pendingCorporateData.getLga(),
                pendingCorporateData.getState()
        ));
        if (!StringUtils.hasText(pendingCorporateData.getCountry())) {
            pendingCorporateData.setCountry("NG");
        }
        if (!StringUtils.hasText(pendingCorporateData.getPostCode())) {
            pendingCorporateData.setPostCode("000000");
        }
        pendingCorporateData.setShareCapital(parseBigDecimal(getValue(cac, "shareCapital")));
        pendingCorporateData.setShareCapitalInWords(getStringValue(cac, "shareCapitalInWords"));
        pendingCorporateData.setNumberOfAffiliates(parseInteger(getValue(cac, "affiliates")));

        String registrationDate = getStringValue(cac, "registrationDate");
        if (registrationDate != null && !registrationDate.isBlank()) {
            try {
                pendingCorporateData.setRegistrationDate(OffsetDateTime.parse(registrationDate));
            } catch (Exception ex) {
                log.warn("Unable to parse CAC registration date: {}", registrationDate);
            }
        }

        pendingCorporateData.setCacVerified(isCacResponseVerifiedStatic(response));
    }

    private static boolean isCacResponseVerifiedStatic(QoreIdCacResponse response) {
        if (response == null) {
            return false;
        }

        if (response.getSummary() != null && response.getSummary().getCacCheck() != null) {
            Object value = response.getSummary().getCacCheck().get("status");
            if (value != null && "verified".equalsIgnoreCase(value.toString())) {
                return true;
            }
        }

        if (response.getStatus() != null) {
            return "verified".equalsIgnoreCase(response.getStatus().getStatus())
                    || "complete".equalsIgnoreCase(response.getStatus().getState());
        }

        return false;
    }

    private static String getStringValue(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    private static String getNestedStringValue(Map<String, Object> source, String parentKey, String childKey) {
        Object parent = getValue(source, parentKey);
        if (!(parent instanceof Map<?, ?> nestedMap)) {
            return null;
        }
        Object value = nestedMap.get(childKey);
        if (value == null) {
            return null;
        }
        return value.toString().trim();
    }

    private static Object getValue(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        return source.get(key);
    }

    private static BigDecimal parseBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = value.toString().trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer integer) {
            return integer;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private boolean isCacResponseVerified(QoreIdCacResponse response) {
        return isCacResponseVerifiedStatic(response);
    }

    private void retirePreviousOtps(String email) {
        List<UserOtp> previous = otpRepository.findAllByEmailIgnoreCase(email);
        if (previous.isEmpty()) {
            return;
        }
        otpRepository.deleteAll(previous);
        log.debug("Retired {} superseded OTP(s) | email={}", previous.size(), email);
    }

    private PendingCorporateData requirePendingCorporateData() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedAccountPrincipal principal)
                || principal.getAccountType() != AccountType.PENDING_CORPORATE_ONBOARDING) {
            throw new ForbiddenException(
                    "This step requires the corporate onboarding token returned by the KYC step.");
        }

        return findPendingCorporateDataForPrincipal(principal)
                .orElseThrow(() -> new ResourceNotFound("No corporate onboarding in progress."));
    }

    private Optional<PendingCorporateData> findPendingCorporateDataForPrincipal(
            AuthenticatedAccountPrincipal principal) {

        Optional<PendingCorporateData> found = Optional.empty();

        if (principal.getId() != null && !principal.getId().isBlank()) {
            found = pendingCorporateDataRepository.findById(principal.getId());
        }

        if (found.isEmpty() && principal.getEmail() != null && !principal.getEmail().isBlank()) {
            found = pendingCorporateDataRepository.findByEmailIgnoreCase(normalizeEmail(principal.getEmail()));
        }

        found.ifPresent(this::attachDirectors);
        return found;
    }

    private void attachDirectors(PendingCorporateData pending) {
        pending.setDirectors(pendingCorporateDirectorRepository.findByPendingCorporateDataId(pending.getId()));
    }
}
