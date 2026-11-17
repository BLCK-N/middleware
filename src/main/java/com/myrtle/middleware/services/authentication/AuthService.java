package com.myrtle.middleware.services.authentication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.myrtle.middleware.dtos.AddHoldersDetailsDto;
import com.myrtle.middleware.dtos.bvn.BVNRequest;
import com.myrtle.middleware.dtos.bvn.BvnValidationResponse;
import com.myrtle.middleware.dtos.bvn.QoreIdBVNResponse;
import com.myrtle.middleware.dtos.bvn.QoreIdBvnRequest;
import com.myrtle.middleware.dtos.request.*;
import com.myrtle.middleware.dtos.response.*;
import com.myrtle.middleware.enums.*;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.exceptions.InvalidRefreshTokenException;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.*;
import com.myrtle.middleware.repository.*;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.corporate.CorporateAccountService;
import com.myrtle.middleware.services.questionnaire.QuestionnaireService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final String CUSTOMER_CARE_EMAIL = "wecare@myrtleng.com";
    private final ResponseUtil responseUtil;
    private final EmailService emailService;
    private final ExternalAPIs externalAPIs;
    private final UserRepository userRepository;
    private final CorporateClientRepository corporateClientRepository;
    private final AddHolderDetailsRepository addHolderDetailsRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtil;
    private final HoldPersonalDetailsRepository holdPersonalDetailsRepository;
    private final FCMTokenRepository fcmTokenRepository;
    private final NextOfKinRepository nextOfKinRepository;
    private final ValidatorUitls validatorUitls;
    private final BvnCheckService bvnCheckService;
    private final BvnCacheRepo bvnCacheRepo;
    private final QuestionnaireService questionnaireService;
    private final WithdrawalAccountRespository withdrawalAccountRespository;
    private final KycService kycService;

    @Autowired
    private BiometricAuthService biometricAuthService;

    @Lazy
    @Autowired
    private CorporateAccountService corporateAccountService;

    @Autowired
    private DeviceDetailsService deviceDetailsService;

    @Lazy
    @Autowired
    private PendingOnboardingService pendingOnboardingService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    // Fixed set of monitors used to serialize concurrent verifications of the same BVN
    // within this instance (reduces duplicate provider calls). Across instances the DB
    // unique constraint on bvn is the real guard.
    private static final int BVN_LOCK_STRIPES = 64;
    private final Object[] bvnLocks = initBvnLocks();

    private static Object[] initBvnLocks() {
        Object[] locks = new Object[BVN_LOCK_STRIPES];
        for (int i = 0; i < BVN_LOCK_STRIPES; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private Object bvnLock(String bvn) {
        return bvnLocks[Math.floorMod(Objects.hashCode(bvn), BVN_LOCK_STRIPES)];
    }

    public ResponseEntity<ApiResponse> sendOtp(SendOtp sendOtp) {
        String email = normalizeEmail(sendOtp.email());
        log.info("OTP send requested | email={} | checkIfExists={}", email, sendOtp.checkifexists());

        if (sendOtp.checkifexists()) {
            // Used for account creation — reject if user already exists
            boolean existsInCRM = externalAPIs.checkIfEmailExists(email);
            boolean existsInDB = userRepository.findByEmailAddress(email).isPresent()
                    || corporateClientRepository.findByEmailIgnoreCase(email).isPresent();

            if (existsInCRM || existsInDB) {
                log.warn("OTP send rejected: account already exists | email={}", email);
                return responseUtil.conflict("Account already exists", null);
            }
        }

        String otpReference = UUID.randomUUID().toString();
        sendNewOtp(email, otpReference);

        HashMap<String, String> map = new HashMap<>();
        map.put("otpReference", otpReference);

        return responseUtil.success("OTP sent successfully", map);
    }

    public void sendNewOtp(String email, String reference) {
        retirePreviousOtps(email);

        String otp = OTPGenerator.generateOtp(6);

        UserOtp newOtp = new UserOtp();
        newOtp.setEmail(email);
        newOtp.setOtp(passwordEncoder.encode(otp));
        newOtp.setOtpReference(reference);
        otpRepository.save(newOtp);

        emailService.deliverOtp(email, otp);
        log.info("OTP sent successfully | email={} | reference={}", email, reference);
    }


    public ResponseEntity<ApiResponse> verifyOtp(VerifyDto verifyDto) {
        if (verifyDto.email() == null || verifyDto.otp() == null) {
            return responseUtil.badRequest("Email or OTP not provided", null);
        }

        String email = normalizeEmail(verifyDto.email());
        log.debug("OTP verification attempt | email={} | reference={}", email, verifyDto.otpReference());

        Optional<UserOtp> userOtpOptional = otpRepository.findOtpByOtpReferenceAndEmail(verifyDto.otpReference(), email);
        if (userOtpOptional.isEmpty()) {
            log.warn("OTP not found | email={} | reference={}", email, verifyDto.otpReference());
            return responseUtil.notFound("OTP not found for this email", null);
        }

        UserOtp userOtp = userOtpOptional.get();
        if (userOtp.isVerified())
            return responseUtil.badRequest("OTP already verified", null);

        else if (userOtp.isExpired()) {
            log.warn("OTP expired | email={} | reference={}", email, verifyDto.otpReference());
            return responseUtil.badRequest("OTP expired. generate an OTP to continue", null);
        }

        if (passwordEncoder.matches(verifyDto.otp(), userOtp.getOtp())) {
            userOtp.markVerified();
            otpRepository.save(userOtp);
            log.info("OTP verified successfully | email={}", email);
            if (pendingOnboardingService != null) {
                pendingOnboardingService.startIndividual(email, verifyDto.otpReference());
            }
            return responseUtil.success("OTP verified successfully", null);
        } else {
            log.warn("Invalid OTP provided | email={}", email);
            return responseUtil.badRequest("Invalid OTP", null);
        }
    }

    public ResponseEntity<ApiResponse> resetPassword(ResetRequest request) {
        String email = normalizeEmail(request.email());
        log.info("Password reset requested | email={}", email);
        Optional<UserOtp> userOtpOptional = otpRepository.findOtpByOtpReferenceAndEmail(request.otpReference(), email);

        if (userOtpOptional.isEmpty() || !userOtpOptional.get().isVerified()) {
            log.warn("Password reset rejected: OTP not verified | email={}", email);
            return responseUtil.unauthorized("OTP not verified. Please verify your email before creating an account.", null);
        }

        User user = userRepository.findByEmailAddress(email).orElseThrow(() -> new ResourceNotFound("User not Found"));

        boolean isCheck = passwordEncoder.matches(request.password(), user.getPassword());
        if (isCheck) {
            log.warn("Password reset rejected: reuse of previous password | email={}", email);
            return responseUtil.unauthorized("You cannot reuse your previous password. Please choose a new one.", null);
        }

        user.setPassword(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        refreshTokenService.revokeForUser(user.getId());
        log.info("Password reset successful | email={}", email);
        return responseUtil.success("Password Updated Successfully", user);
    }

    public ResponseEntity<ApiResponse> login(UserLogin userLogin) throws JsonProcessingException {
        return login(userLogin, null, null);
    }

    public ResponseEntity<ApiResponse> login(UserLogin userLogin, String deviceId, String deviceType) throws JsonProcessingException {
        String identifier = userLogin == null ? null : userLogin.resolvedEmail();
        log.info("Login attempt | identifier={}", identifier);

        if (identifier == null || identifier.isBlank()) {
            return responseUtil.badRequest("Email or username is required.", null);
        }

        identifier = normalizeEmail(identifier);
        String bioToken = userLogin == null ? null : userLogin.bioToken();
        if (bioToken != null && !bioToken.isBlank()) {
            User user = userRepository.findByEmailAddress(identifier).orElse(null);
            if (user == null) {
                return responseUtil.notFound("No account was found with the provided email address. Please create an account to continue.", null);
            }

            User validatedUser = biometricAuthService.validateBioToken(identifier, bioToken, deviceId, deviceType);
            if (validatedUser == null || !validatedUser.getId().equals(user.getId())) {
                return responseUtil.unauthorized("Invalid biometric credentials for this device.", null);
            }
            return issueTokenBundle(user, userLogin, "Login Successful");
        }

        if (validatorUitls.isEmail(identifier)) {
            User user = userRepository.findByEmailAddress(identifier).orElse(null);
            if (user != null) {
                return loginUser(userLogin, user);
            }

            CorporateClient corporateClient = corporateClientRepository.findByEmailIgnoreCase(identifier)
                    .orElse(null);
            if (corporateClient != null) {
                return corporateAccountService.authenticateCorporateClient(userLogin, corporateClient);
            }

            return responseUtil.notFound("No account was found with the provided email address. Please create an account to continue.", null);
        }

        return responseUtil.fromFunctionResponse(onboardFromMyWealth(userLogin));
    }

    public ResponseEntity<ApiResponse> refreshSession(RefreshTokenRequest request) {
        if (request == null || !StringUtils.hasText(request.refreshToken())) {
            return responseUtil.unauthorized(RefreshTokenService.INVALID_REFRESH_TOKEN, null);
        }

        try {
            IssuedTokens tokens = refreshTokenService.rotate(request.refreshToken().trim());
            return responseUtil.success("Token refreshed successfully", tokens);
        } catch (InvalidRefreshTokenException ex) {
            return responseUtil.unauthorized(ex.getMessage(), null);
        }
    }

    private ResponseEntity<ApiResponse> loginUser(UserLogin userLogin, User user) {
        String rawPassword = userLogin == null ? null : userLogin.password();
        boolean isPasswordCorrect = rawPassword != null && passwordEncoder.matches(rawPassword, user.getPassword());

        if (!isPasswordCorrect) {
            log.warn("Login failed: incorrect password | email={}", user.getEmailAddress());
            return responseUtil.badRequest("The credentials provided are invalid. Please verify your details and try again.", null);
        }

        if (user.isDeleted()) {
            log.warn("Login blocked: account deleted | email={}", user.getEmailAddress());
            return responseUtil.forbidden("Account has been deleted", null);
        }

        if (user.isDisabled()) {
            log.warn("Login blocked: account disabled | email={}", user.getEmailAddress());
            return responseUtil.forbidden("Account disabled. Please reach out to admin at wecare@myrtleng.com", null);
        }

        return issueTokenBundle(user, userLogin, "Login Successful");
    }

    private ResponseEntity<ApiResponse> issueTokenBundle(User user, UserLogin userLogin, String message) {
        String accessToken = jwtUtil.generateToken(user);
        String refreshToken = refreshTokenService.issueForUser(user);
        boolean isBvnVerified = user.getBvn() != null && !"UNVERIFIED".equalsIgnoreCase(user.getBvn());
        boolean isNinVerified = user.getNin() != null && !user.getNin().isBlank();
        boolean kycCompleted = isTierAtLeast(user.getTier(), 2);

        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("isBvnVerified", isBvnVerified);
        data.put("isNinVerified", isNinVerified);
        data.put("kycCompleted", kycCompleted);
        data.put("myWealthData", null);
        data.put("expires_in", jwtUtil.accessTokenTtlSeconds());
        data.put("token_type", "Bearer");
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshToken);
        data.put("refresh_expires_in", 30L * 24L * 60L * 60L);
        attachSavedDevice(data, user, userLogin == null ? null : userLogin.device());

        log.info("User login successful | email={}", user.getEmailAddress());
        return responseUtil.success(message, data);
    }

    private FunctionResponse onboardFromMyWealth(UserLogin userLogin) throws JsonProcessingException {
        log.info("MyWealth onboarding flow started | username={}", userLogin.emailAddress());

        ClientLoginResponse wealthLoginResponse = authenticateMyWealthClient(userLogin);
        String clientId = wealthLoginResponse.getId();

        ClientLoginResponse.Contact contact = resolvePrimaryContact(wealthLoginResponse);
        String email = normalizeEmail(contact.getEmail());

        User existingUser = userRepository.findByEmailAddress(email).orElse(null);
        if (existingUser != null) {
            return loginExistingMyWealthUser(userLogin, existingUser);
        }

        List<KycDocument> kycDocs = externalAPIs.getKycDocuments(clientId);

        User newUser = buildUserFromMyWealthContact(contact, userLogin, clientId);
        assignBranchAndInvestmentAccount(newUser, contact, clientId);
        applyMyWealthProfileDetails(newUser, contact, wealthLoginResponse);

        String bvn = resolveBvnFromMyWealth(contact, kycDocs);
        String nin = resolveNinFromMyWealth(contact, kycDocs);
        VerifiedIdentity verifiedIdentity = new VerifiedIdentity();

        boolean isBvnVerified = validateAndApplyBvn(newUser, bvn, verifiedIdentity);
        boolean isNinVerified = validateAndApplyNin(newUser, nin, verifiedIdentity);

        newUser.setTier(calculateKycTier(newUser, isBvnVerified, isNinVerified, wealthLoginResponse));

        userRepository.save(newUser);
        syncVerifiedIdentityToMyWealth(newUser, contact, wealthLoginResponse, verifiedIdentity);
        linkSettlementBanks(newUser, wealthLoginResponse, verifiedIdentity.bvn());

        log.info(
                "New user onboarded from MyWealth | email={} | clientId={} | tier={} | isBvnVerified={} | isNinVerified={}",
                newUser.getEmailAddress(),
                clientId,
                newUser.getTier(),
                isBvnVerified,
                isNinVerified
        );

        return buildMyWealthOnboardingLoginResponse(newUser, wealthLoginResponse, isBvnVerified, isNinVerified, userLogin.device());
    }

    private ClientLoginResponse authenticateMyWealthClient(UserLogin userLogin) throws JsonProcessingException {
        LoginRequest loginRequest = new LoginRequest(
                userLogin.emailAddress(),
                userLogin.password(),
                userLogin.ipAddress(),
                "session-" + System.currentTimeMillis()
        );

        ExternalLoginResult externalResult = externalAPIs.loginClient(loginRequest);

        if (externalResult == null || !externalResult.isSuccess()) {
            log.warn(
                    "MyWealth authentication failed | username={} | errorCode={}",
                    userLogin.emailAddress(),
                    externalResult != null ? externalResult.getErrorCode() : "null"
            );

            if (externalResult != null && "VALIDATION_FAILED".equals(externalResult.getErrorCode())) {
                ExternalLoginResult.ExternalErrorResponse externalErrorResponse = externalResult.getMessage();
                ObjectMapper objectMapper = new ObjectMapper();
                JsonNode rootNode = objectMapper.readTree(externalErrorResponse.getDetails());
                String message = rootNode.get("details").asText();
                throw new FunctionException("Login Failed", message);
            }
            if (externalResult != null && "NOT_FOUND".equals(externalResult.getErrorCode())) {
                throw new FunctionException(
                        "No account matches the username and password provided. Please check your details and try again.",
                        null);
            }
            throw new FunctionException("Authentication failed", null);
        }

        ClientLoginResponse wealthLoginResponse = externalResult.getData();
        if (wealthLoginResponse == null || wealthLoginResponse.getId() == null) {
            throw new FunctionException("User not found on MyWealth.", null);
        }

        if (!"NGN".equals(wealthLoginResponse.getValuationCurrency())) {
            throw new FunctionException("The Account is not a Naira Account", null);
        }

        return wealthLoginResponse;
    }

    private ClientLoginResponse.Contact resolvePrimaryContact(ClientLoginResponse wealthLoginResponse) {
        if (wealthLoginResponse.getContact() == null || wealthLoginResponse.getContact().isEmpty()) {
            throw new FunctionException("Invalid contact data from MyWealth.", null);
        }

        return wealthLoginResponse.getContact().stream()
                .filter(c -> c.getRole() == MY_WEALTH_ROLE.INDV_OWNER)
                .findFirst()
                .orElseThrow(() -> new FunctionException("Primary contact not found on MyWealth client.", null));
    }

    private FunctionResponse loginExistingMyWealthUser(UserLogin userLogin, User existingUser) {
        log.debug("Existing local user found for MyWealth login | email={}", existingUser.getEmailAddress());

        if (!passwordEncoder.matches(userLogin.password(), existingUser.getPassword())) {
            log.warn("MyWealth login failed: local password mismatch | email={}", existingUser.getEmailAddress());
            throw new FunctionException("Invalid credentials.", null);
        }

        String token = jwtUtil.generateToken(existingUser.getEmailAddress());
        Map<String, Object> data = new HashMap<>();
        data.put("user", existingUser);
        data.put("isBvnVerified", isVerifiedBvn(existingUser.getBvn()));
        data.put("isNinVerified", StringUtils.hasText(existingUser.getNin()));
        data.put("kycCompleted", isTierAtLeast(existingUser.getTier(), 2));
        data.put("myWealthData", null);
        data.put("accessToken", token);
        data.put("refreshToken", refreshTokenService.issueForUser(existingUser));
        attachSavedDevice(data, existingUser, userLogin.device());

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Login Successful")
                .data(data)
                .build();
    }

    private User buildUserFromMyWealthContact(
            ClientLoginResponse.Contact contact,
            UserLogin userLogin,
            String clientId
    ) {
        User newUser = new User();
        newUser.setEmailAddress(normalizeEmail(contact.getEmail()));
        newUser.setFirstName(contact.getFirstName());
        newUser.setLastName(contact.getLastName());
        newUser.setMiddleName(StringUtils.hasText(contact.getMiddleName()) ? contact.getMiddleName() : contact.getFirstName());
        newUser.setPassword(passwordEncoder.encode(userLogin.password()));
        newUser.setGender(contact.getGender());
        newUser.setPhoneNumber(contact.getMobileNo());
        newUser.setRole(Role.USER);
        newUser.setClientId(clientId);
        newUser.setBvn("UNVERIFIED");
        return newUser;
    }

    private void assignBranchAndInvestmentAccount(User newUser, ClientLoginResponse.Contact contact, String clientId) {
        PagedLocationResponse branchResponse = externalAPIs.getBranch();
        if (branchResponse != null
                && branchResponse.getContent() != null
                && !branchResponse.getContent().isEmpty()) {
            newUser.setBranchId(branchResponse.getContent().get(0).getId());
        }

        List<InvestmentAccountResponse2> investmentAccounts = externalAPIs.getInvestmentAccounts(clientId);
        if (investmentAccounts != null && !investmentAccounts.isEmpty()) {
            newUser.setInvestmentAccountId(investmentAccounts.get(0).getId());
            return;
        }

        String fullName = contact.getFirstName() + " " + contact.getLastName();
        InvestmentAccountRequest investmentAccountRequest = new InvestmentAccountRequest(
                clientId,
                "",
                newUser.getBranchId(),
                InvestmentAccountRequest.AccountUsage.LIVE,
                InvestmentAccountRequest.MgmtType.SELF,
                fullName,
                true,
                true
        );

        InvestmentAccountResponse created = externalAPIs.createAnInvestmentAccount(investmentAccountRequest);
        if (created != null) {
            newUser.setInvestmentAccountId(created.getId());
        }
    }

    private String resolveBvnFromMyWealth(ClientLoginResponse.Contact contact, List<KycDocument> kycDocs) {
        if (contact != null && StringUtils.hasText(contact.getFinIdNo()) && contact.getFinIdNo().matches("\\d{11}")) {
            return contact.getFinIdNo().trim();
        }

        if (contact != null
                && StringUtils.hasText(contact.getIdNo())
                && contact.getIdType() != null
                && contact.getIdType().toUpperCase(Locale.ROOT).contains("BVN")) {
            return contact.getIdNo().trim();
        }

        return extractIdentityNumberFromKycDocuments(kycDocs, "BVN");
    }

    private String resolveNinFromMyWealth(ClientLoginResponse.Contact contact, List<KycDocument> kycDocs) {
        if (contact != null
                && StringUtils.hasText(contact.getIdNo())
                && contact.getIdType() != null) {
            String idType = contact.getIdType().toUpperCase(Locale.ROOT);
            if (idType.contains("NIN") || idType.contains("NATIONAL")) {
                return contact.getIdNo().trim();
            }
        }

        return extractIdentityNumberFromKycDocuments(kycDocs, "NIN", "NATIONAL_ID");
    }

    private String extractIdentityNumberFromKycDocuments(List<KycDocument> kycDocs, String... typeKeywords) {
        if (kycDocs == null) {
            return null;
        }

        for (KycDocument doc : kycDocs) {
            if (doc == null || !StringUtils.hasText(doc.getIdNo())) {
                continue;
            }

            String type = doc.getIdType() != null ? doc.getIdType().name().toUpperCase(Locale.ROOT) : "";
            for (String keyword : typeKeywords) {
                if (type.contains(keyword)) {
                    return doc.getIdNo().trim();
                }
            }
        }
        return null;
    }

    private boolean validateAndApplyBvn(User user, String bvn, VerifiedIdentity verifiedIdentity) {
        if (!StringUtils.hasText(bvn)) {
            return false;
        }

        Optional<User> existingBvnUser = userRepository.findByBvn(bvn);
        if (existingBvnUser.isPresent()) {
            throw new FunctionException("BVN already exists on another account.", null);
        }

        QoreIdBvnRequest bvnRequest = new QoreIdBvnRequest(
                bvn,
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber()
        );

        QoreIdBVNResponse qoreIdBvnResponse;
        try {
            qoreIdBvnResponse = externalAPIs.verifyBvnPremium(bvnRequest);
        } catch (FunctionException e) {
            log.warn("MyWealth onboarding BVN verification rejected by QoreID | email={} | reason={}",
                    user.getEmailAddress(), e.getMessage());
            return false;
        }
        if (qoreIdBvnResponse == null || qoreIdBvnResponse.getBvn() == null) {
            return false;
        }

        QoreIdBVNResponse.Bvn bvnRecord = qoreIdBvnResponse.getBvn();
        String registeredName = getNormalizedName(user.getFirstName(), user.getMiddleName(), user.getLastName());
        String bvnName = getNormalizedName(bvnRecord.getFirstname(), bvnRecord.getMiddlename(), bvnRecord.getLastname());
        if (!bvnCheckService.checkNameMatch(bvnName, registeredName)) {
            log.warn(
                    "MyWealth onboarding BVN name mismatch - using verified BVN data | email={} | registeredName={} | bvnName={}",
                    user.getEmailAddress(),
                    registeredName,
                    bvnName
            );
        }

        applyVerifiedBvnToUser(user, bvn, bvnRecord, verifiedIdentity);
        cacheBvn(bvnRecord, bvn);
        return true;
    }

    private void applyVerifiedBvnToUser(
            User user,
            String bvn,
            QoreIdBVNResponse.Bvn bvnRecord,
            VerifiedIdentity verifiedIdentity
    ) {
        user.setBvn(bvn);
        if (StringUtils.hasText(bvnRecord.getFirstname())) {
            user.setFirstName(bvnRecord.getFirstname().trim());
        }
        if (StringUtils.hasText(bvnRecord.getLastname())) {
            user.setLastName(bvnRecord.getLastname().trim());
        }
        if (StringUtils.hasText(bvnRecord.getMiddlename())) {
            user.setMiddleName(bvnRecord.getMiddlename().trim());
        }
        String gender = validatorUitls.normalizeGenderValue(bvnRecord.getGender());
        if (StringUtils.hasText(gender)) {
            user.setGender(gender);
        }
        if (StringUtils.hasText(bvnRecord.getPhone())
                && !samePhoneNumber(user.getPhoneNumber(), bvnRecord.getPhone())) {
            user.setPhoneNumber(bvnRecord.getPhone().trim());
        }
        if (!StringUtils.hasText(user.getAddress()) && StringUtils.hasText(bvnRecord.getResidentialAddress())) {
            user.setAddress(bvnRecord.getResidentialAddress().trim());
        }

        verifiedIdentity.bvnVerified = true;
        verifiedIdentity.bvn = bvn;
        verifiedIdentity.firstName = user.getFirstName();
        verifiedIdentity.lastName = user.getLastName();
        verifiedIdentity.middleName = user.getMiddleName();
        verifiedIdentity.gender = user.getGender();
        verifiedIdentity.phone = user.getPhoneNumber();
        verifiedIdentity.birthDate = PendingCorporateOnboardingMapper.normalizeBirthDate(bvnRecord.getBirthdate());
        verifiedIdentity.maritalStatus = normalizeMyWealthMaritalStatus(bvnRecord.getMaritalStatus());
        verifiedIdentity.title = normalizeMyWealthTitle(bvnRecord.getTitle());
        verifiedIdentity.nationality = blankToNull(bvnRecord.getNationality());
        verifiedIdentity.stateOfOrigin = blankToNull(bvnRecord.getStateOfOrigin());
        verifiedIdentity.localGovt = blankToNull(bvnRecord.getLgaOfOrigin());
    }

    private boolean validateAndApplyNin(User user, String nin, VerifiedIdentity verifiedIdentity) {
        if (!StringUtils.hasText(nin)) {
            return false;
        }

        NinVerificationRequest ninRequest = new NinVerificationRequest(
                nin,
                user.getFirstName(),
                user.getLastName()
        );

        NinVerificationResponse ninResponse;
        try {
            ninResponse = externalAPIs.verifyNin(ninRequest);
        } catch (FunctionException e) {
            log.warn("MyWealth onboarding NIN verification rejected by QoreID | email={} | reason={}",
                    user.getEmailAddress(), e.getMessage());
            return false;
        }
        if (ninResponse == null
                || ninResponse.getNinRecord() == null
                || !StringUtils.hasText(ninResponse.getNinRecord().getNin())) {
            return false;
        }

        if (!isQoreIdNinCheckSuccessful(ninResponse)) {
            return false;
        }

        NinVerificationResponse.NinRecord ninRecord = ninResponse.getNinRecord();
        String registeredName = getNormalizedName(user.getFirstName(), user.getMiddleName(), user.getLastName());
        String ninName = getNormalizedName(
                ninRecord.getFirstname(),
                ninRecord.getMiddlename(),
                ninRecord.getLastname()
        );

        if (!bvnCheckService.checkNameMatch(ninName, registeredName)) {
            log.warn(
                    "MyWealth onboarding NIN name mismatch - using verified NIN data | email={} | registeredName={} | ninName={}",
                    user.getEmailAddress(),
                    registeredName,
                    ninName
            );
        }

        user.setNin(nin);
        if (!verifiedIdentity.bvnVerified) {
            if (StringUtils.hasText(ninRecord.getFirstname())) {
                user.setFirstName(ninRecord.getFirstname().trim());
            }
            if (StringUtils.hasText(ninRecord.getLastname())) {
                user.setLastName(ninRecord.getLastname().trim());
            }
            if (StringUtils.hasText(ninRecord.getMiddlename())) {
                user.setMiddleName(ninRecord.getMiddlename().trim());
            }
            String gender = validatorUitls.normalizeGenderValue(ninRecord.getGender());
            if (StringUtils.hasText(gender)) {
                user.setGender(gender);
            }
            if (StringUtils.hasText(ninRecord.getPhone())
                    && !samePhoneNumber(user.getPhoneNumber(), ninRecord.getPhone())) {
                user.setPhoneNumber(ninRecord.getPhone().trim());
            }
        }

        verifiedIdentity.ninVerified = true;
        verifiedIdentity.nin = nin;
        verifiedIdentity.firstName = user.getFirstName();
        verifiedIdentity.lastName = user.getLastName();
        verifiedIdentity.middleName = user.getMiddleName();
        verifiedIdentity.gender = user.getGender();
        verifiedIdentity.phone = user.getPhoneNumber();
        return true;
    }

    private String calculateKycTier(
            User user,
            boolean isBvnVerified,
            boolean isNinVerified,
            ClientLoginResponse wealthLoginResponse
    ) {
        if (user != null && "3".equals(user.getTier())) {
            return "3";
        }

        String mappedTier = mapMyWealthKycTierCode(wealthLoginResponse.getKycTierCode());
        if (mappedTier != null) {
            return mappedTier;
        }

        if (isNinVerified) {
            return "2";
        }
        if (isBvnVerified) {
            return "1";
        }
        return "1";
    }

    private String mapMyWealthKycTierCode(String kycTierCode) {
        if (!StringUtils.hasText(kycTierCode)) {
            return null;
        }

        String normalized = kycTierCode.trim().toUpperCase(Locale.ROOT);
        if (normalized.matches("\\d+")) {
            return normalized;
        }

        return switch (normalized) {
            case "TIER_1", "T1" -> "1";
            case "TIER_2", "T2" -> "2";
            case "TIER_3", "T3" -> "3";
            default -> null;
        };
    }

    private void applyMyWealthProfileDetails(
            User user,
            ClientLoginResponse.Contact contact,
            ClientLoginResponse wealthLoginResponse
    ) {
        if (contact.getAddress() != null && StringUtils.hasText(contact.getAddress().getAddressLine1())) {
            user.setAddress(contact.getAddress().getAddressLine1());
        } else if (wealthLoginResponse.getAddress() != null) {
            wealthLoginResponse.getAddress().stream()
                    .filter(address -> address != null && "PRIMARY".equalsIgnoreCase(address.getType()))
                    .findFirst()
                    .map(ClientLoginResponse.Address::getAddressLine1)
                    .filter(StringUtils::hasText)
                    .ifPresent(user::setAddress);
        }

        if (StringUtils.hasText(contact.getPictureUrl())) {
            user.setProfileImage(contact.getPictureUrl());
        } else if (StringUtils.hasText(wealthLoginResponse.getPictureUrl())) {
            user.setProfileImage(wealthLoginResponse.getPictureUrl());
        }
    }

    private void syncVerifiedIdentityToMyWealth(
            User user,
            ClientLoginResponse.Contact contact,
            ClientLoginResponse wealthLoginResponse,
            VerifiedIdentity verifiedIdentity
    ) {
        if (verifiedIdentity == null || (!verifiedIdentity.bvnVerified && !verifiedIdentity.ninVerified)) {
            return;
        }
        if (contact == null || !StringUtils.hasText(contact.getId())) {
            log.warn("Skipping MyWealth identity sync: missing contact id | email={}", user.getEmailAddress());
            return;
        }
        if (!identityDiffersFromMyWealth(contact, wealthLoginResponse, verifiedIdentity)) {
            return;
        }

        PatchClientRequest patchRequest = buildVerifiedIdentityPatchRequest(user, contact, verifiedIdentity);
        ClientResponse patchResponse = externalAPIs.updatePatchClient(user.getClientId(), patchRequest);
        if (patchResponse == null) {
            log.warn("MyWealth identity sync failed during login migration | email={} | clientId={}",
                    user.getEmailAddress(), user.getClientId());
            return;
        }
        log.info("Synced verified identity to MyWealth | email={} | clientId={}", user.getEmailAddress(), user.getClientId());
    }

    private boolean identityDiffersFromMyWealth(
            ClientLoginResponse.Contact contact,
            ClientLoginResponse wealthLoginResponse,
            VerifiedIdentity verifiedIdentity
    ) {
        if (!equalsIgnoreCaseTrim(contact.getFirstName(), verifiedIdentity.firstName)
                || !equalsIgnoreCaseTrim(contact.getLastName(), verifiedIdentity.lastName)
                || !equalsIgnoreCaseTrim(blankToNull(contact.getMiddleName()), blankToNull(verifiedIdentity.middleName))) {
            return true;
        }
        if (!equalsIgnoreCaseTrim(
                validatorUitls.normalizeGenderValue(contact.getGender()),
                validatorUitls.normalizeGenderValue(verifiedIdentity.gender))) {
            return true;
        }
        if (StringUtils.hasText(verifiedIdentity.phone)
                && !samePhoneNumber(contact.getMobileNo(), verifiedIdentity.phone)
                && !samePhoneNumber(wealthLoginResponse.getMobileNo(), verifiedIdentity.phone)) {
            return true;
        }
        if (verifiedIdentity.bvnVerified && !equalsIgnoreCaseTrim(contact.getFinIdNo(), verifiedIdentity.bvn)) {
            return true;
        }
        if (verifiedIdentity.ninVerified && !equalsIgnoreCaseTrim(contact.getIdNo(), verifiedIdentity.nin)) {
            return true;
        }
        if (StringUtils.hasText(verifiedIdentity.birthDate)
                && (contact.getBirthDate() == null
                || !verifiedIdentity.birthDate.equals(contact.getBirthDate().toString()))) {
            return true;
        }
        if (StringUtils.hasText(verifiedIdentity.maritalStatus)
                && !equalsIgnoreCaseTrim(contact.getMaritalStatus(), verifiedIdentity.maritalStatus)) {
            return true;
        }
        if (StringUtils.hasText(verifiedIdentity.title)
                && !equalsIgnoreCaseTrim(contact.getTitle(), verifiedIdentity.title)) {
            return true;
        }
        if (StringUtils.hasText(verifiedIdentity.stateOfOrigin)
                && !equalsIgnoreCaseTrim(contact.getStateOfOrigin(), verifiedIdentity.stateOfOrigin)) {
            return true;
        }
        if (StringUtils.hasText(verifiedIdentity.localGovt)
                && !equalsIgnoreCaseTrim(contact.getLocalGovt(), verifiedIdentity.localGovt)) {
            return true;
        }
        return false;
    }

    private PatchClientRequest buildVerifiedIdentityPatchRequest(
            User user,
            ClientLoginResponse.Contact contact,
            VerifiedIdentity verifiedIdentity
    ) {
        PatchClientRequest request = new PatchClientRequest();
        request.setEmail(user.getEmailAddress());
        request.setNotificationEmail(user.getEmailAddress());
        request.setMobileNo(blankToNull(user.getPhoneNumber()));

        PatchClientRequest.Contact patchContact = new PatchClientRequest.Contact();
        patchContact.setId(contact.getId());
        patchContact.setRole("INDV_OWNER");
        patchContact.setFirstName(user.getFirstName());
        patchContact.setMiddleName(user.getMiddleName());
        patchContact.setLastName(user.getLastName());
        patchContact.setEmail(user.getEmailAddress());
        patchContact.setMobileNo(blankToNull(user.getPhoneNumber()));
        patchContact.setGender(validatorUitls.normalizeGenderValue(user.getGender()));
        patchContact.setBirthDate(verifiedIdentity.birthDate);
        patchContact.setMaritalStatus(verifiedIdentity.maritalStatus);
        patchContact.setTitle(verifiedIdentity.title);
        patchContact.setNationality(verifiedIdentity.nationality);
        patchContact.setStateOfOrigin(verifiedIdentity.stateOfOrigin);
        patchContact.setLocalGovt(verifiedIdentity.localGovt);

        if (verifiedIdentity.bvnVerified) {
            patchContact.setFinIdNo(verifiedIdentity.bvn);
        }
        if (verifiedIdentity.ninVerified) {
            patchContact.setIdType("NATIONAL_ID");
            patchContact.setIdNo(verifiedIdentity.nin);
        } else if (StringUtils.hasText(contact.getIdType())
                && !contact.getIdType().toUpperCase(Locale.ROOT).contains("BVN")) {
            patchContact.setIdType(contact.getIdType());
            patchContact.setIdNo(contact.getIdNo());
        }

        request.setContact(List.of(patchContact));
        return request;
    }

    private void linkSettlementBanks(User user, ClientLoginResponse wealthLoginResponse, String bvn) {
        if (wealthLoginResponse.getSettlementBanks() == null || wealthLoginResponse.getSettlementBanks().isEmpty()) {
            return;
        }

        List<WithdrawalAccount> existingAccounts = withdrawalAccountRespository.findByUser_Id(user.getId());
        Set<String> existingAccountNumbers = existingAccounts == null
                ? new HashSet<>()
                : existingAccounts.stream()
                .map(WithdrawalAccount::getAccountNumber)
                .filter(StringUtils::hasText)
                .map(this::digitsOnly)
                .collect(Collectors.toCollection(HashSet::new));

        int remainingSlots = 3 - (existingAccounts == null ? 0 : existingAccounts.size());
        if (remainingSlots <= 0) {
            return;
        }

        List<ClientLoginResponse.SettlementBank> banks = wealthLoginResponse.getSettlementBanks().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((ClientLoginResponse.SettlementBank bank) ->
                        !"PRIMARY".equalsIgnoreCase(bank.getType())))
                .toList();

        String fallbackBvn = StringUtils.hasText(bvn) && !"UNVERIFIED".equalsIgnoreCase(bvn) ? bvn : user.getBvn();

        for (ClientLoginResponse.SettlementBank bank : banks) {
            if (remainingSlots <= 0) {
                break;
            }
            if (!StringUtils.hasText(bank.getStlBankAccountNo()) || !StringUtils.hasText(bank.getStlBankRoutingNo())) {
                log.warn("Skipping settlement bank during MyWealth onboarding: incomplete bank details | email={}",
                        user.getEmailAddress());
                continue;
            }

            String accountNumber = bank.getStlBankAccountNo().trim();
            String accountNumberKey = digitsOnly(accountNumber);
            if (!StringUtils.hasText(accountNumberKey) || existingAccountNumbers.contains(accountNumberKey)) {
                continue;
            }

            String verifiedAccountName = resolveSettlementAccountName(bank, user);
            if (!StringUtils.hasText(verifiedAccountName)) {
                log.warn("Skipping settlement bank during MyWealth onboarding: missing account name | email={}",
                        user.getEmailAddress());
                continue;
            }

            WithdrawalAccount withdrawalAccount = new WithdrawalAccount();
            withdrawalAccount.setBankCode(bank.getStlBankRoutingNo().trim());
            withdrawalAccount.setBankName(validatorUitls.firstNonBlank(bank.getStlBankName(), bank.getStlBankRoutingNo()));
            withdrawalAccount.setAccountNumber(accountNumber);
            withdrawalAccount.setAccountName(verifiedAccountName);
            withdrawalAccount.setBankVerificationNumber(
                    StringUtils.hasText(fallbackBvn) && !"UNVERIFIED".equalsIgnoreCase(fallbackBvn) ? fallbackBvn : null
            );
            withdrawalAccount.setUser(user);
            withdrawalAccount.setOwnerType(AccountOwnerType.USER);
            withdrawalAccountRespository.save(withdrawalAccount);

            existingAccountNumbers.add(accountNumberKey);
            remainingSlots--;
        }
    }

    private String resolveSettlementAccountName(ClientLoginResponse.SettlementBank bank, User user) {
        BankAccountRequest bankAccountRequest = new BankAccountRequest();
        bankAccountRequest.setAccountNo(bank.getStlBankAccountNo());
        bankAccountRequest.setBankCode(bank.getStlBankRoutingNo());

        try {
            BankAccountResponse bankAccountResponse = externalAPIs.nameInquiry(bankAccountRequest);
            if (bankAccountResponse != null
                    && "00".equals(bankAccountResponse.getResponseCode())
                    && StringUtils.hasText(bankAccountResponse.getAccountName())) {
                return bankAccountResponse.getAccountName().trim().toUpperCase(Locale.ROOT);
            }
        } catch (Exception ex) {
            log.warn("Settlement bank name inquiry failed during MyWealth onboarding | email={} | reason={}",
                    user.getEmailAddress(), ex.getMessage());
        }

        if (StringUtils.hasText(bank.getStlBankAccountName())) {
            return bank.getStlBankAccountName().trim().toUpperCase(Locale.ROOT);
        }
        return blankToNull(getNormalizedName(user.getFirstName(), user.getMiddleName(), user.getLastName()));
    }

    private FunctionResponse buildMyWealthOnboardingLoginResponse(
            User user,
            ClientLoginResponse wealthLoginResponse,
            boolean isBvnVerified,
            boolean isNinVerified,
            DeviceDetailsRequest device
    ) {
        String accessToken = jwtUtil.generateToken(user.getEmailAddress());

        Map<String, Object> advisorDetails = new LinkedHashMap<>();
        advisorDetails.put("advisorId", wealthLoginResponse.getAdvisorId());
        advisorDetails.put("advisorCode", wealthLoginResponse.getAdvisorCode());
        advisorDetails.put("advisorLabel", wealthLoginResponse.getAdvisorLabel());
        advisorDetails.put("groupId", wealthLoginResponse.getGroupId());
        advisorDetails.put("groupCode", wealthLoginResponse.getGroupCode());
        advisorDetails.put("groupLabel", wealthLoginResponse.getGroupLabel());
        advisorDetails.put("referrerId", wealthLoginResponse.getReferrerId());
        advisorDetails.put("referrerLabel", wealthLoginResponse.getReferrerLabel());
        advisorDetails.put("referralCode", wealthLoginResponse.getReferralCode());

        Map<String, Object> data = new HashMap<>();
        data.put("user", user);
        data.put("isBvnVerified", isBvnVerified);
        data.put("isNinVerified", isNinVerified);
        data.put("kycCompleted", isTierAtLeast(user.getTier(), 2));
        data.put("myWealthData", wealthLoginResponse);
        data.put("advisorDetails", advisorDetails);
        data.put("accessToken", accessToken);
        data.put("refreshToken", refreshTokenService.issueForUser(user));
        attachSavedDevice(data, user, device);

        return FunctionResponse.builder()
                .isSuccess(true)
                .status(HttpStatus.OK)
                .message("Login Successful")
                .data(data)
                .build();
    }

    private void attachSavedDevice(Map<String, Object> data, User user, DeviceDetailsRequest device) {
        if (deviceDetailsService == null) {
            return;
        }
        DeviceDetailsResponse saved = deviceDetailsService.saveFromLogin(user, device);
        if (saved != null) {
            data.put("device", saved);
        }
    }

    private boolean isVerifiedBvn(String bvn) {
        return StringUtils.hasText(bvn) && !"UNVERIFIED".equalsIgnoreCase(bvn);
    }

    public ResponseEntity<ApiResponse> addProfileImage(String email, String url) {

        Optional<User> optionalUser = userRepository.findByEmailAddress(email);

        if (optionalUser.isEmpty()) {
            return responseUtil.notFound("User not found", null);
        }

        if (url == null || url.trim().isEmpty()) {
            return responseUtil.badRequest("Profile image URL is required", null);
        }

        if (!validatorUitls.isImageUrl(url)) {
            return responseUtil.badRequest("Invalid image format. Only image files are allowed", null);
        }

        User user = optionalUser.get();
        user.setProfileImage(url);
        userRepository.save(user);

        return responseUtil.success("Profile image updated successfully", null);
    }

    @Transactional
    public ResponseEntity<ApiResponse> collectAndUpdateBVN(String email, String bvn) {
        log.info("BVN collection/update requested | email={}", email);

        if (email == null || email.isBlank()) {
            return badRequest("Email address is required.");
        }

        if (bvn == null || bvn.isBlank()) {
            return badRequest("BVN is required.");
        }

        if (!bvn.matches("\\d{11}")) {
            return badRequest("Invalid BVN format. BVN must be 11 digits.");
        }

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() ->
                        new ResourceNotFound("User not found.")
                );

        if (bvn.equals(user.getBvn())) {
            return responseUtil.success("BVN already verified for this account.", null);
        }

        userRepository.findByBvn(bvn)
                .filter(existing -> !existing.getEmailAddress().equalsIgnoreCase(email))
                .ifPresent(existing -> {
                    throw new ResourceNotFound("This BVN is already linked to another account.");
                });

        QoreIdBvnRequest request = new QoreIdBvnRequest(
                bvn,
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber()
        );

        QoreIdBVNResponse qoreIdBvnResponse = externalAPIs.verifyBvnPremium(request);


        bvnCheckService.bvnValidationAlgorithm(qoreIdBvnResponse, user);

//        if (user.getAccountNumber() == null) {
//
//            BVNResponse.Bvn bvnDetails = bvnResponse.getBvn();
//            String residentialAddress = (bvnDetails.getResidentialAddress() == null || bvnDetails.getResidentialAddress().isBlank())
//                    ? "Nigeria"
//                    : bvnDetails.getResidentialAddress().trim();
//            String stateInput = (bvnDetails.getStateOfResidence() == null || bvnDetails.getStateOfResidence().isBlank())
//                    ? bvnDetails.getStateOfOrigin()
//                    : bvnDetails.getStateOfResidence();
//            String resolvedState = resolveNigerianStateName(stateInput, residentialAddress);
//            String resolvedStateCode = validatorUitls.resolveNigerianStateCode(stateInput, residentialAddress);
//
//            GeminiAccountCreationRequest accountRequest =
//                    new GeminiAccountCreationRequest(
//                            user.getFirstName() + " " + user.getLastName(),
//                            bvn,
//                            residentialAddress,
//                            resolvedState,
//                            user.getEmailAddress(),
//                            user.getPhoneNumber(),
//                            "43004532132",
//                            resolvedStateCode,
//                            "MYTLE"
//                    );
//
//            GeminiAccountCreationResponse geminiResponse =
//                    externalAPIs.createAccount(accountRequest);
//
//            if (geminiResponse == null
//                    || !"00".equalsIgnoreCase(geminiResponse.getResponseCode())) {
//
//                return serverError("BVN verified, but Gemini account creation failed.");
//            }
//
//            user.setAccountNumber(geminiResponse.getAccountNumber());
//        }

        user.setBvn(bvn);
        user.setMiddleName(qoreIdBvnResponse.getBvn().getMiddlename());
        userRepository.save(user);

        String accessToken = jwtUtil.generateToken(user.getEmailAddress());

        log.info("BVN verified and updated | email={} | bvn={}", email, maskBvn(bvn));
        return responseUtil.success("KYC credential updated successfully.",
                Map.of("accessToken", accessToken));
    }

    public ResponseEntity<ApiResponse> addPin(String email, String pin, boolean usePin) {
        Optional<User> optionalUser = userRepository.findByEmailAddress(email);

        if (optionalUser.isEmpty()) {
            return responseUtil.notFound("User not found with email: " + email, null);
        }

        User user = optionalUser.get();
        String hashedPIN = passwordEncoder.encode(pin);
        user.setPin(hashedPIN);
        user.setUsePin(true);
        userRepository.save(user);

        return responseUtil.success("PIN added successfully", null);
    }

    @Transactional
    public ResponseEntity<ApiResponse> setPinForAccount(String email, String pin) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();

        if (!StringUtils.hasText(normalizedEmail)) {
            return responseUtil.badRequest("Email is required", null);
        }

        if (!StringUtils.hasText(pin) || !pin.matches("^\\d{4}$")) {
            return responseUtil.badRequest("PIN must be exactly 4 digits", null);
        }

        Optional<CorporateClient> corporateOptional = corporateClientRepository.findByEmailIgnoreCase(normalizedEmail);
        if (corporateOptional.isPresent()) {
            CorporateClient corporateClient = corporateOptional.get();
            String hashedPIN = passwordEncoder.encode(pin);
            corporateClient.setPinHash(hashedPIN);
            corporateClient.setPinSet(true);
            corporateClient.setPinEnabled(true);
            corporateClientRepository.save(corporateClient);
            log.info("PIN set for corporate account | email={} | corporateClientId={}", normalizedEmail, corporateClient.getId());
            return responseUtil.success("PIN set successfully for corporate account", null);
        }

        Optional<User> userOptional = userRepository.findByEmailAddress(normalizedEmail);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            String hashedPIN = passwordEncoder.encode(pin);
            user.setPin(hashedPIN);
            user.setUsePin(true);
            userRepository.save(user);
            log.info("PIN set for user | email={}", normalizedEmail);
            return responseUtil.success("PIN set successfully", null);
        }

        return responseUtil.notFound("Account not found with email: " + email, null);
    }

    public ResponseEntity<ApiResponse> changePIN(String email, ChangePinRequest request) {

        Optional<User> optionalUser = userRepository.findByEmailAddress(email);

        if (optionalUser.isEmpty()) {
            return responseUtil.notFound("User not found with email: " + email, null);
        }

        Optional<UserOtp> userOtpOptional = otpRepository.findOtpByOtpReferenceAndEmail(request.otpReference(), email);
        if (userOtpOptional.isEmpty() || !userOtpOptional.get().isVerified()) {
            return responseUtil.badRequest("OTP not found or not verified for this email", null);
        }

        User user = optionalUser.get();
        String hashedPIN = passwordEncoder.encode(request.pin());
        user.setPin(hashedPIN);
        user.setUsePin(true);

        userRepository.save(user);

        return responseUtil.success("PIN updated successfully", null);
    }

    public ResponseEntity<ApiResponse> loginWithPin(String email, String pin) {
        log.debug("PIN login attempt | email={}", email);
        Optional<User> optionalUser = userRepository.findByEmailAddress(email);

        if (optionalUser.isEmpty()) {
            return responseUtil.badRequest("Invalid email or PIN", null);
        }

        User user = optionalUser.get();

        if (!user.isUsePin()) {
            log.warn("PIN login rejected: PIN login disabled | email={}", email);
            return responseUtil.badRequest("PIN login is disabled for this account", null);
        }

        if (user.getPin() == null || user.getPin().isBlank()) {
            return responseUtil.badRequest("Invalid email or PIN", null);
        }

        boolean isValid = passwordEncoder.matches(pin, user.getPin());

        if (!isValid) {
            log.warn("PIN login failed: incorrect PIN | email={}", email);
            return responseUtil.badRequest("Incorrect PIN", null);
        }

        log.info("PIN login successful | email={}", email);
        return responseUtil.success("Login successful", user);
    }

    private PatchClientRequest buildTier3AddressPatchRequest(User user, String tier3Address) {
        PatchClientRequest request = new PatchClientRequest();

        String residentialAddress = validatorUitls.firstNonBlank(tier3Address, user.getAddress(), "Nigeria");
        String stateCode = validatorUitls.resolveNigerianStateCode(null, residentialAddress);
        String stateName = validatorUitls.resolveNigerianStateName(null, residentialAddress);

        PatchClientRequest.Address address = new PatchClientRequest.Address();
        address.setType("PRIMARY");
        address.setAddressLine1(residentialAddress);
        address.setAddressLine2(residentialAddress);
        address.setCity(stateName);
        address.setState("NG-" + stateCode);
        address.setPostCode("100001");
        address.setCountry("NG");

        PatchClientRequest.Contact contact = new PatchClientRequest.Contact();
        contact.setRole("INDV_OWNER");
        contact.setFirstName(validatorUitls.firstNonBlank(user.getFirstName()));
        contact.setMiddleName(validatorUitls.firstNonBlank(user.getMiddleName()));
        contact.setLastName(validatorUitls.firstNonBlank(user.getLastName()));
        contact.setEmail(user.getEmailAddress());
        contact.setMobileNo(blankToNull(user.getPhoneNumber()));
        contact.setGender(validatorUitls.normalizeGenderValue(validatorUitls.firstNonBlank(user.getGender())));
        contact.setAddress(address);

        if (user.getBvn() != null && !user.getBvn().isBlank() && !"UNVERIFIED".equalsIgnoreCase(user.getBvn())) {
            contact.setIdType("BVN");
            contact.setIdNo(user.getBvn());
            contact.setFinIdNo(user.getBvn());
        }

        request.setEmail(user.getEmailAddress());
        request.setNotificationEmail(user.getEmailAddress());
        request.setMobileNo(blankToNull(user.getPhoneNumber()));
        request.setAddress(List.of(address));
        request.setContact(List.of(contact));

        return request;
    }


    public ResponseEntity<ApiResponse> getUserProfile(String email) {

        if (email == null || email.trim().isEmpty()) {
            return responseUtil.badRequest("Email is required", null);
        }

        Optional<User> userOpt = userRepository.findByEmailAddress(email);

        if (userOpt.isEmpty()) {
            return responseUtil.notFound("User not found", null);
        }

        User user = userOpt.get();

        HoldPersonalDetailsDto pendingDto = holdPersonalDetailsRepository
                .findByUser_EmailAddress(email)
                .map(h -> new HoldPersonalDetailsDto(
                        h.getId(),
                        safe(h.getFirstName()),
                        safe(h.getLastName()),
                        safe(h.getMiddleName()),
                        safe(h.getPhoneNumber()),
                        safe(h.getReason_for_change()),
                        h.getStatus(),
                        h.getProof_image()
                ))
                .orElse(null);

        AddHoldersDetailsDto tierDetailsDto = addHolderDetailsRepository
                .findByUser(user)
                .map(d -> new AddHoldersDetailsDto(
                        d.getId(),
                        safe(d.getAddress()),
                        d.getProof(),
                        d.getProofType(),
                        d.getStatus()
                ))
                .orElse(null);

        boolean filledSurvey = questionnaireService.isSurveyFilled(email);

        UserProfileDto userProfileDto = new UserProfileDto(
                user.getId(),
                safe(user.getFirstName()),
                safe(user.getLastName()),
                safe(user.getMiddleName()),
                safe(user.getEmailAddress()),
                safe(user.getPhoneNumber()),
                safe(user.getClientId()),
                safe(user.getBranchId()),
                safe(user.getAccountNumber()),
                safe(user.getInvestmentAccountId()),
                user.getRole() != null ? user.getRole().name() : null,
                safe(user.getTier()),
                safe(user.getGender()),
                user.getAddress() != null ? user.getAddress() : null,
                user.isUsePin(),
                String.valueOf(user.getLast_balance()),
                user.getProfileImage(),
                user.isPep(),
                user.getPepPosition()
        );

        List<NextOfKin> nextOfKinDto = nextOfKinRepository.findByUser_Id(user.getId());

        boolean isNextOfKinAvaliable = !nextOfKinDto.isEmpty();

        Map<String, Object> response = new HashMap<>();
        response.put("user", userProfileDto);
        response.put("pendingDetails", pendingDto);
        response.put("tierDetails", tierDetailsDto);
        response.put("isNextOfKinAvaliable", isNextOfKinAvaliable);
        response.put("filledSurvey", filledSurvey);

        return responseUtil.success("Profile fetched successfully", response);
    }

    public ResponseEntity<ApiResponse> getUserIdentityDetails(String email) {
        User user = userRepository.findByEmailAddress(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        List<NextOfKinResponse> nextOfKins = nextOfKinRepository.findByUser_Id(user.getId()).stream()
                .map(this::toNextOfKinResponse)
                .toList();

        UserIdentityDetailsResponse payload = new UserIdentityDetailsResponse(
                user.getAddress(),
                user.getNin(),
                user.getBvn(),
                nextOfKins
        );

        return responseUtil.success("User identity details fetched successfully", payload);
    }

    private NextOfKinResponse toNextOfKinResponse(NextOfKin entity) {
        return new NextOfKinResponse(
                entity.getId(),
                entity.getUser().getId(),
                entity.getFullName(),
                entity.getRelationship(),
                entity.getPhoneNumber(),
                entity.getEmail(),
                entity.getAddress(),
                entity.isPrimary(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public ResponseEntity<ApiResponse> updatePepDetails(String email, UpdatePepRequest request) {
        if (request == null) {
            return responseUtil.badRequest("PEP payload is required", null);
        }

        User user = userRepository.findByEmailAddress(normalizeEmail(email))
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        String pepPosition = StringUtils.hasText(request.pepPosition())
                ? request.pepPosition().trim()
                : null;

        if (request.isPep() && pepPosition == null) {
            return responseUtil.badRequest("pepPosition is required when isPep is true", null);
        }

        user.setPep(request.isPep());
        user.setPepPosition(request.isPep() ? pepPosition : null);
        userRepository.save(user);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("isPep", user.isPep());
        payload.put("pepPosition", user.getPepPosition());

        return responseUtil.success("PEP details updated successfully", payload);
    }

    public ResponseEntity<ApiResponse> editUserDetails(EditUserRequest editUserRequest, String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        Optional<HoldPersonalDetails> holdPersonalDetailsOpt =
                holdPersonalDetailsRepository.findByUser_EmailAddress(email);

        if (holdPersonalDetailsOpt.isPresent()) {
            HoldPersonalDetails details = holdPersonalDetailsOpt.get();

            switch (details.getStatus()) {
                case VERIFIED:
                    return responseUtil.success("Processing successfully verified", details);

                case PENDING:
                    return responseUtil.accepted("Request is still pending approval", details);

                case NOT_VERIFIED:
                    return responseUtil.badRequest("Request was rejected", details);

                default:
                    return responseUtil.serverError("Unknown status", details);
            }
        }

        HoldPersonalDetails holdPersonalDetails = new HoldPersonalDetails();
        holdPersonalDetails.setFirstName(editUserRequest.firstname());
        holdPersonalDetails.setMiddleName(editUserRequest.middleName() != null ? editUserRequest.middleName() : "");
        holdPersonalDetails.setLastName(editUserRequest.lastname());
        holdPersonalDetails.setProof_image(editUserRequest.imageProof());
        holdPersonalDetails.setPhoneNumber(editUserRequest.phoneNumber());
        holdPersonalDetails.setReason_for_change(editUserRequest.reason());
        holdPersonalDetails.setStatus(Status.PENDING);
        holdPersonalDetails.setUser(user);

        holdPersonalDetailsRepository.save(holdPersonalDetails);

        String access_token = jwtUtil.generateToken(CUSTOMER_CARE_EMAIL);
        emailService.sendMailToCustomerCare(CUSTOMER_CARE_EMAIL, editUserRequest, access_token);

        return responseUtil.success("User details submitted for review and mail sent to customer care", holdPersonalDetails);
    }

    public ResponseEntity<ApiResponse> upgradeToTier3(Tier3UpgradePayLoad tier3UpgradePayLoad, String email) {
        log.info("Tier 3 upgrade requested | email={}", email);

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found for email: " + email));

        if (user.getTier() == null || Integer.parseInt(user.getTier()) < 2) {
            return responseUtil.conflict("Please upgrade your account to Tier 2 first.", null);
        }

        if ("3".equals(user.getTier())) {
            return responseUtil.conflict("You have already upgraded to Tier 3.", null);
        }

        if (tier3UpgradePayLoad == null
                || !StringUtils.hasText(tier3UpgradePayLoad.address())
                || tier3UpgradePayLoad.proofType() == null) {
            return responseUtil.badRequest("address, proofType and proof are required", null);
        }

        List<String> proofUrls = normalizeProofUrls(tier3UpgradePayLoad.proof());
        if (proofUrls.isEmpty()) {
            return responseUtil.badRequest("At least one valid proof URL is required", null);
        }

        kycService.submitTier3Review(
                user,
                tier3UpgradePayLoad.address().trim(),
                tier3UpgradePayLoad.proofType(),
                proofUrls
        );

        String accessToken = jwtUtil.generateToken(user.getEmailAddress());
        emailService.sendMailToCustomerCareUpgrade(
                CUSTOMER_CARE_EMAIL,
                new Tier3UpgradePayLoad(
                        tier3UpgradePayLoad.address().trim(),
                        proofUrls.toArray(String[]::new),
                        tier3UpgradePayLoad.proofType()
                ),
                accessToken,
                user
        );

        return responseUtil.success("Tier 3 upgrade request submitted successfully. Awaiting customer care approval.",
                Map.of(
                        "email", email,
                        "tier", user.getTier(),
                        "status", Status.PENDING.name()
                ));
    }

    @Transactional
    public ResponseEntity<ApiResponse> migrateTierOneToThree(TierMigrationPayload payload, String email) {
        log.info("Tier migration requested | email={}", email);

        if (payload == null
                || payload.idNumber() == null || payload.idNumber().isBlank()
                || payload.firstname() == null || payload.firstname().isBlank()
                || payload.lastname() == null || payload.lastname().isBlank()
                || payload.address() == null || payload.address().isBlank()
                || payload.proofType() == null
                || payload.proof() == null || payload.proof().length == 0) {
            return responseUtil.badRequest("idNumber, firstname, lastname, address, proofType and proof are required", null);
        }

        Optional<User> userOptional = userRepository.findByEmailAddress(email);
        if (userOptional.isEmpty()) {
            Optional<CorporateClient> corporateClientOptional = corporateClientRepository.findByEmailIgnoreCase(email);
            if (corporateClientOptional.isPresent()) {
                CorporateClient corporateClient = corporateClientOptional.get();
                boolean changed = false;
                if (corporateClient.getRole() == null) {
                    corporateClient.setRole(Role.CORPORATE_ACCOUNT);
                    changed = true;
                }
                if (corporateClient.getTier() == null) {
                    corporateClient.setTier(3);
                    changed = true;
                }
                if (changed) {
                    corporateClientRepository.save(corporateClient);
                }

                return responseUtil.success("Corporate account already uses Tier 3 access.",
                        Map.of(
                                "email", corporateClient.getEmail(),
                                "tier", corporateClient.getTier(),
                                "accountType", AccountType.CORPORATE_ACCOUNT.name()
                        ));
            }
            throw new ResourceNotFound("User not found with email: " + email);
        }

        User user = userOptional.get();

        if ("3".equals(user.getTier())) {
            return responseUtil.conflict("You have already upgraded to Tier 3.", null);
        }

        NinVerificationRequest ninRequest = new NinVerificationRequest(
                payload.idNumber(),
                user.getFirstName(),
                user.getLastName()
        );

        NinVerificationResponse ninVerificationResponse = externalAPIs.verifyNin(ninRequest);

        if (ninVerificationResponse == null
                || ninVerificationResponse.getNinRecord() == null
                || ninVerificationResponse.getNinRecord().getNin() == null
                || ninVerificationResponse.getNinRecord().getNin().isEmpty()) {
            return responseUtil.badRequest("Invalid or unverifiable NIN. Please check and try again.", null);
        }

        String fullName = String.format("%s %s %s",
                user.getFirstName() != null ? user.getFirstName().trim() : "",
                user.getMiddleName() != null ? user.getMiddleName().trim() : "",
                user.getLastName() != null ? user.getLastName().trim() : "").trim();

        String fullNameFromNIN = String.format("%s %s %s",
                ninVerificationResponse.getNinRecord().getFirstname() != null ? ninVerificationResponse.getNinRecord().getFirstname().trim() : "",
                ninVerificationResponse.getNinRecord().getMiddlename() != null ? ninVerificationResponse.getNinRecord().getMiddlename().trim() : "",
                ninVerificationResponse.getNinRecord().getLastname() != null ? ninVerificationResponse.getNinRecord().getLastname().trim() : "").trim();

        if (!fullNameFromNIN.equalsIgnoreCase(fullName)) {
            log.warn("Tier migration failed: NIN name mismatch | email={} | registeredName={} | ninName={}", email, fullName, fullNameFromNIN);
            return responseUtil.badRequest("Name on NIN record does not match your registered name.",
                    Map.of(
                            "ninName", fullNameFromNIN,
                            "registeredName", fullName
                    ));
        }

        List<String> proofUrls = normalizeProofUrls(payload.proof());
        if (proofUrls.isEmpty()) {
            return responseUtil.badRequest("idNumber, firstname, lastname, address, proofType and at least one valid proof URL are required", null);
        }

        AddHolderDetails addHolderDetails = addHolderDetailsRepository
                .findByUser(user)
                .orElse(new AddHolderDetails());

        addHolderDetails.setUser(user);
        addHolderDetails.setAddress(payload.address());
        addHolderDetails.setProof(KycService.mergeProofUrls(addHolderDetails.getProof(), proofUrls));
        addHolderDetails.setProofType(payload.proofType());
        addHolderDetails.setStatus(Status.VERIFIED);
        addHolderDetails.setCreatedAt(LocalDateTime.now());
        addHolderDetailsRepository.save(addHolderDetails);

        user.setNin(payload.idNumber());
        user.setTier("3");
        userRepository.save(user);

        log.info("Tier migration successful | email={} | tier=3", email);
        return responseUtil.success("Tier migration successful. Account upgraded to Tier 3.",
                Map.of(
                        "email", user.getEmailAddress(),
                        "tier", user.getTier(),
                        "ninVerified", true,
                        "addressStatus", Status.VERIFIED.name()
                ));
    }

    public ResponseEntity<ApiResponse> verifyBVN(BVNRequest request) {
        try {

            if (request.newUser()) {
                Optional<User> optUser = userRepository.findByBvn(request.bvn());
                if (optUser.isPresent()) {
                    return responseUtil.badRequest("An account already exists with this BVN", null);
                }
            }

            // Serialize concurrent verifications of the same BVN within this instance so we
            // don't fire duplicate provider calls or race on the cache write.
            synchronized (bvnLock(request.bvn())) {
                BvnCache cached = bvnCacheRepo.findBvnCacheByBvn(request.bvn());
                if (cached != null && cached.isVerified()) {
                    log.info("BVN cache hit | bvn={}", maskBvn(request.bvn()));
                    return respondFromCache(cached, request);
                }

                log.info("BVN cache miss, calling verification provider | bvn={}", maskBvn(request.bvn()));
                QoreIdBvnRequest qoreIdBvnRequest = new QoreIdBvnRequest(request.bvn(), request.firstName(), request.lastName(), request.phoneNumber());
                QoreIdBVNResponse qoreIdBvnResponse = externalAPIs.verifyBvnPremium(qoreIdBvnRequest);

                if (qoreIdBvnResponse == null) {
                    log.warn("BVN verification failed | response is null");
                    return responseUtil.serviceUnavailable("Invalid BVN. Check your details and retry.", null);
                }
                if (qoreIdBvnResponse.getBvn() == null) {
                    throw new FunctionException("Your BVN could not be validated", null);
                }

                try {
                    BvnCache bvnCache = cacheBvn(qoreIdBvnResponse.getBvn(), request.bvn());
                    bvnCheckService.bvnValidationAlgorithm(qoreIdBvnResponse, request);
                    bvnCache.setVerified(true);
                    bvnCacheRepo.save(bvnCache);

                    BvnValidationResponse bvnValidationResponse = new BvnValidationResponse(qoreIdBvnResponse.getBvn().getMiddlename(), true);
                    trackIndividualBvnVerified(request);
                    return responseUtil.success("BVN successfully verified.", bvnValidationResponse);
                } catch (DataIntegrityViolationException dup) {
                    // Another request (likely a different instance) cached this BVN concurrently.
                    // Resolve as a cache hit instead of inserting a duplicate row.
                    log.info("BVN cache write conflict, resolving as cache hit | bvn={}", maskBvn(request.bvn()));
                    BvnCache existing = bvnCacheRepo.findBvnCacheByBvn(request.bvn());
                    if (existing != null) {
                        return respondFromCache(existing, request);
                    }
                    throw dup;
                }
            }
        } catch (FunctionException e) {
            throw e;
        } catch (Exception ex) {
            log.error("BVN verification | unexpected error", ex);
            return responseUtil.serverError("An unexpected error occurred while verifying BVN.", ex.getMessage());
        }
    }

    private ResponseEntity<ApiResponse> respondFromCache(BvnCache cached, BVNRequest request) {
        bvnCheckService.bvnValidationAlgorithm(toQoreIdResponse(cached), request);
        trackIndividualBvnVerified(request);
        BvnValidationResponse cachedResponse = new BvnValidationResponse(cached.getMiddleName(), true);
        return responseUtil.success("BVN successfully verified.", cachedResponse);
    }

    private void trackIndividualBvnVerified(BVNRequest request) {
        if (pendingOnboardingService == null || request == null) {
            return;
        }
        pendingOnboardingService.recordIndividualBvnVerified(
                request.email(), request.firstName(), request.lastName(), request.phoneNumber());
    }

    public BvnCache cacheBvn(QoreIdBVNResponse.Bvn response, String bvn) {
        // Upsert: reuse the existing row for this BVN if present, otherwise create one.
        // Avoids inserting duplicate rows for the same BVN and works with the unique
        // constraint on the bvn column.
        BvnCache cache = bvnCacheRepo.findBvnCacheByBvn(bvn);
        if (cache == null) {
            cache = new BvnCache();
            cache.setId(UUID.randomUUID().toString());
        }
        cache.setBvn(bvn);
        cache.setBirthdate(response.getBirthdate());
        cache.setFirstName(response.getFirstname());
        cache.setMiddleName(response.getMiddlename());
        cache.setLastName(response.getLastname());
        cache.setPhone(response.getPhone());
        cache.setEmail(response.getEmail());
        cache.setGender(response.getGender());
        cache.setMaritalStatus(response.getMaritalStatus());
        cache.setStateOfResidence(response.getStateOfResidence());
        cache.setStateOfOrigin(response.getStateOfOrigin());
        cache.setResidentialAddress(response.getResidentialAddress());
        cache.setNationality(response.getNationality());
        cache.setLgaOfOrigin(response.getLgaOfOrigin());
        return bvnCacheRepo.save(cache);
    }

    public QoreIdBVNResponse toQoreIdResponse(BvnCache cache) {
        QoreIdBVNResponse.Bvn bvn = QoreIdBVNResponse.Bvn.builder()
                .bvn(cache.getBvn())
                .firstname(cache.getFirstName())
                .middlename(cache.getMiddleName())
                .lastname(cache.getLastName())
                .birthdate(cache.getBirthdate())
                .gender(cache.getGender())
                .phone(cache.getPhone())
                .email(cache.getEmail())
                .build();
        return QoreIdBVNResponse.builder().bvn(bvn).build();
    }

    public ResponseEntity<ApiResponse> verifyNIN(NinVerificationRequest request, String email) {
        log.info("NIN verification requested | email={}", email);
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        String fullName = getNormalizedName(
                user.getFirstName(),
                user.getMiddleName(),
                user.getLastName()
        );

        NinVerificationResponse ninVerificationResponse = externalAPIs.verifyNin(request);

        if (ninVerificationResponse == null ||
                ninVerificationResponse.getNinRecord() == null ||
                ninVerificationResponse.getNinRecord().getNin() == null ||
                ninVerificationResponse.getNinRecord().getNin().isEmpty()) {
            return responseUtil.badRequest("Invalid or unverifiable NIN. Please check and try again.", null);
        }

        String fullNameFromNIN = getNormalizedName(
                ninVerificationResponse.getNinRecord().getFirstname(),
                ninVerificationResponse.getNinRecord().getMiddlename(),
                ninVerificationResponse.getNinRecord().getLastname()
        );

        boolean checkIfMatches = bvnCheckService.checkNameMatch(fullNameFromNIN, fullName);
        if (!checkIfMatches) {
            log.warn("NIN verification failed: name mismatch | email={} | registeredName={} | ninName={}", email, fullName, fullNameFromNIN);
            return responseUtil.conflict("Account Name Does not Match Credentials", null);
        }

        user.setNin(request.idNumber());
        user.setTier("2");
        userRepository.save(user);

        log.info("NIN verified successfully | email={} | tier=2", email);
        return responseUtil.success("NIN successfully verified and account upgraded.", ninVerificationResponse);
    }

    public ResponseEntity<ApiResponse> upgradeToTier2(Tier2UpgradePayload payload, String email) {
        log.info("Tier 2 upgrade requested | email={}", email);

        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        if ("3".equals(user.getTier())) {
            return responseUtil.conflict("You have already upgraded to Tier 3.", null);
        }

        if (isTierAtLeast(user.getTier(), 2)) {
            return responseUtil.conflict("You have already upgraded to Tier 2.", null);
        }

        List<String> proofUrls = normalizeProofUrls(payload == null ? null : payload.proof());
        if (proofUrls.isEmpty()) {
            return responseUtil.badRequest("At least one valid proof URL is required", null);
        }

        String firstName = StringUtils.hasText(payload.firstname()) ? payload.firstname().trim() : user.getFirstName();
        String lastName = StringUtils.hasText(payload.lastname()) ? payload.lastname().trim() : user.getLastName();
        NinVerificationRequest ninRequest = new NinVerificationRequest(payload.idNumber(), firstName, lastName);

        Optional<ResponseEntity<ApiResponse>> ninValidationError = validateNinForTierUpgrade(user, ninRequest);
        if (ninValidationError.isPresent()) {
            return ninValidationError.get();
        }

        if (!StringUtils.hasText(user.getClientId())) {
            return responseUtil.badRequest("Client profile is missing. Complete account setup before upgrading to Tier 2.", null);
        }

        List<KycDocument> uploadedDocuments = uploadTier2IdentityDocuments(
                user, payload.proofType(), payload.idNumber(), proofUrls);
        if (uploadedDocuments == null) {
            return responseUtil.badGateway("Identity document upload failed. Please try again.", null);
        }

        PatchClientRequest patchRequest = buildTier2NinPatchRequest(user, payload.idNumber());
        ClientResponse patchResponse = externalAPIs.updatePatchClient(user.getClientId(), patchRequest);
        if (patchResponse == null) {
            log.warn("Tier 2 NIN patch returned null for clientId={}", user.getClientId());
            return responseUtil.badGateway("Unable to sync NIN details upstream. Please try again.", null);
        }

        kycService.completeTier2Upgrade(user, payload.proofType(), proofUrls, payload.idNumber());

        log.info("Tier 2 upgrade successful | email={} | documentsUploaded={}", email, uploadedDocuments.size());
        return responseUtil.success("NIN verified and Tier 2 upgrade completed successfully.", Map.of(
                "email", email,
                "tier", user.getTier(),
                "ninVerified", true,
                "documentsUploaded", uploadedDocuments.size(),
                "documents", uploadedDocuments
        ));
    }

    private Optional<ResponseEntity<ApiResponse>> validateNinForTierUpgrade(User user, NinVerificationRequest request) {
        NinVerificationResponse ninVerificationResponse = externalAPIs.verifyNin(request);

        if (ninVerificationResponse == null
                || ninVerificationResponse.getNinRecord() == null
                || !StringUtils.hasText(ninVerificationResponse.getNinRecord().getNin())) {
            return Optional.of(responseUtil.badRequest("Invalid or unverifiable NIN. Please check and try again.", null));
        }

        if (!isQoreIdNinCheckSuccessful(ninVerificationResponse)) {
            return Optional.of(responseUtil.badRequest("NIN verification failed. Please confirm your NIN and name.", null));
        }

        String fullName = getNormalizedName(user.getFirstName(), user.getMiddleName(), user.getLastName());
        String fullNameFromNin = getNormalizedName(
                ninVerificationResponse.getNinRecord().getFirstname(),
                ninVerificationResponse.getNinRecord().getMiddlename(),
                ninVerificationResponse.getNinRecord().getLastname()
        );

        if (!bvnCheckService.checkNameMatch(fullNameFromNin, fullName)) {
            log.warn("Tier 2 upgrade failed: NIN name mismatch | email={} | registeredName={} | ninName={}",
                    user.getEmailAddress(), fullName, fullNameFromNin);
            return Optional.of(responseUtil.conflict("Account Name Does not Match Credentials", Map.of(
                    "registeredName", fullName,
                    "ninName", fullNameFromNin
            )));
        }

        return Optional.empty();
    }

    private boolean isQoreIdNinCheckSuccessful(NinVerificationResponse ninVerificationResponse) {
        if (ninVerificationResponse.getSummary() == null || ninVerificationResponse.getSummary().getNinCheck() == null) {
            return true;
        }

        String status = ninVerificationResponse.getSummary().getNinCheck().getStatus();
        return !StringUtils.hasText(status) || "verified".equalsIgnoreCase(status.trim());
    }

    private List<KycDocument> uploadTier2IdentityDocuments(
            User user,
            com.myrtle.middleware.enums.ProofType proofType,
            String idNumber,
            List<String> proofUrls
    ) {
        List<KycDocument> uploadedDocuments = new ArrayList<>();
        String idType = mapProofTypeToKycIdType(proofType);
        Set<String> existingUrls = existingMyWealthFileUrls(user.getClientId());

        for (String proofUrl : proofUrls) {
            if (existingUrls.contains(proofUrl)) {
                log.info("Skipping duplicate MyWealth KYC document | clientId={} | fileUrl={}",
                        user.getClientId(), proofUrl);
                continue;
            }

            CreateKycDocumentRequest documentRequest = CreateKycDocumentRequest.builder()
                    .clientId(user.getClientId())
                    .recordType(KycDocument.RecordType.IDENTITY.name())
                    .idType(idType)
                    .idNo(idNumber)
                    .fileUrl(proofUrl)
                    .fileName(extractFileName(proofUrl))
                    .fileType(guessFileType(proofUrl))
                    .build();

            KycDocument uploaded = externalAPIs.createKycDocument(documentRequest);
            if (uploaded == null) {
                log.warn("Tier 2 identity document upload failed | email={} | fileUrl={}", user.getEmailAddress(), proofUrl);
                return null;
            }
            uploadedDocuments.add(uploaded);
            existingUrls.add(proofUrl);
        }

        return uploadedDocuments;
    }

    private Set<String> existingMyWealthFileUrls(String clientId) {
        try {
            List<KycDocument> existing = externalAPIs.getKycDocuments(clientId);
            if (existing == null || existing.isEmpty()) {
                return new HashSet<>();
            }
            return existing.stream()
                    .map(KycDocument::getFileUrl)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .collect(Collectors.toCollection(HashSet::new));
        } catch (Exception ex) {
            log.warn("Unable to list existing MyWealth KYC documents for idempotency | clientId={} | reason={}",
                    clientId, ex.getMessage());
            return new HashSet<>();
        }
    }

    private PatchClientRequest buildTier2NinPatchRequest(User user, String nin) {
        PatchClientRequest request = new PatchClientRequest();

        PatchClientRequest.Contact contact = new PatchClientRequest.Contact();
        contact.setRole("INDV_OWNER");
        contact.setFirstName(user.getFirstName());
        contact.setMiddleName(user.getMiddleName());
        contact.setLastName(user.getLastName());
        contact.setEmail(user.getEmailAddress());
        contact.setMobileNo(blankToNull(user.getPhoneNumber()));
        contact.setGender(validatorUitls.normalizeGenderValue(user.getGender()));
        contact.setIdType("NATIONAL_ID");
        contact.setIdNo(nin);
        contact.setFinIdNo(nin);

        request.setEmail(user.getEmailAddress());
        request.setNotificationEmail(user.getEmailAddress());
        request.setMobileNo(blankToNull(user.getPhoneNumber()));
        request.setContact(List.of(contact));

        return request;
    }

    private String mapProofTypeToKycIdType(com.myrtle.middleware.enums.ProofType proofType) {
        if (proofType == null) {
            return KycDocument.IdType.NATIONAL_ID.name();
        }

        return switch (proofType) {
            case PASSPORT -> KycDocument.IdType.PASSPORT.name();
            case DRIVER_LICENSE -> KycDocument.IdType.DRIVERS_LICENSE.name();
            case VOTER_CARD -> KycDocument.IdType.VOTER_ID.name();
            case BANK_STATEMENT -> KycDocument.IdType.BANK_STATEMENT.name();
            case ELECTRICITY_BILL, WATER_BILL, WASTE_BILL, TENANCY_AGREEMENT, GOVERNMENT_ID ->
                    KycDocument.IdType.OTHER_DOCUMENTS.name();
            default -> KycDocument.IdType.NATIONAL_ID.name();
        };
    }

    private String extractFileName(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return "identity-document";
        }

        String trimmed = fileUrl.trim();
        int queryIndex = trimmed.indexOf('?');
        if (queryIndex >= 0) {
            trimmed = trimmed.substring(0, queryIndex);
        }

        int slashIndex = trimmed.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < trimmed.length() - 1) {
            return trimmed.substring(slashIndex + 1);
        }

        return trimmed;
    }

    private String guessFileType(String fileUrl) {
        String fileName = extractFileName(fileUrl).toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    static List<String> normalizeProofUrls(String[] proof) {
        if (proof == null || proof.length == 0) {
            return List.of();
        }
        List<String> urls = new ArrayList<>();
        for (String proofUrl : proof) {
            if (!isUsableProofUrl(proofUrl)) {
                continue;
            }
            urls.add(proofUrl.trim());
        }
        return urls;
    }

    private static boolean isUsableProofUrl(String proofUrl) {
        if (!StringUtils.hasText(proofUrl)) {
            return false;
        }
        String trimmed = proofUrl.trim();
        return trimmed.startsWith("https://") || trimmed.startsWith("http://");
    }

    private String getNormalizedName(String... names) {
        return Arrays.stream(names).filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(" "))
                .toUpperCase()
                .trim();
    }

    private boolean equalsIgnoreCaseTrim(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return true;
        }
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    private boolean samePhoneNumber(String left, String right) {
        String leftDigits = digitsOnly(left);
        String rightDigits = digitsOnly(right);
        if (!StringUtils.hasText(leftDigits) || !StringUtils.hasText(rightDigits)) {
            return false;
        }
        return normalizePhoneDigits(leftDigits).equals(normalizePhoneDigits(rightDigits));
    }

    private String digitsOnly(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("\\D", "");
    }

    private String normalizePhoneDigits(String digits) {
        String normalized = digits;
        if (normalized.startsWith("234") && normalized.length() > 10) {
            normalized = normalized.substring(3);
        }
        if (normalized.startsWith("0") && normalized.length() > 10) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("0")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeMyWealthMaritalStatus(String maritalStatus) {
        if (!StringUtils.hasText(maritalStatus)) {
            return null;
        }
        String normalized = maritalStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("MARRIED") && !normalized.contains("UNMARRIED")) {
            return "MARRIED";
        }
        if (normalized.contains("SINGLE") || normalized.contains("UNMARRIED")) {
            return "SINGLE";
        }
        return null;
    }

    private String normalizeMyWealthTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String normalized = title.trim().toUpperCase(Locale.ROOT).replace(".", "");
        return switch (normalized) {
            case "MR", "MISTER" -> "MR";
            case "MRS" -> "MRS";
            case "MISS", "MS" -> "MISS";
            case "DR", "DOCTOR" -> "DR";
            case "PROF", "PROFESSOR" -> "PROF";
            case "REV", "REVEREND" -> "REV";
            default -> "OTHER_TITLES";
        };
    }

    private static final class VerifiedIdentity {
        private boolean bvnVerified;
        private boolean ninVerified;
        private String bvn;
        private String nin;
        private String firstName;
        private String lastName;
        private String middleName;
        private String gender;
        private String phone;
        private String birthDate;
        private String maritalStatus;
        private String title;
        private String nationality;
        private String stateOfOrigin;
        private String localGovt;

        private String bvn() {
            return bvn;
        }
    }

    public ResponseEntity<ApiResponse> changePassword(String email, ChangePassword changePassword) {
        log.info("Password change requested | email={}", email);
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        if (!passwordEncoder.matches(changePassword.oldPassword(), user.getPassword())) {
            log.warn("Password change failed: incorrect old password | email={}", email);
            return responseUtil.unauthorized("Old password is incorrect", null);
        }

        if (changePassword.oldPassword().equals(changePassword.newPassword())) {
            return responseUtil.conflict("New password cannot be the same as the old password", null);
        }

        if (passwordEncoder.matches(changePassword.newPassword(), user.getPassword())) {
            return responseUtil.conflict("New password cannot be the same as the current password", null);
        }

        user.setPassword(passwordEncoder.encode(changePassword.newPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeForUser(user.getId());

        log.info("Password changed successfully | email={}", email);
        return responseUtil.success("Password updated successfully", null);
    }

    public ResponseEntity<ApiResponse> disableAccount(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        user.setDisabled(true);
        userRepository.save(user);
        refreshTokenService.revokeForUser(user.getId());
        log.info("Account disabled | email={}", email);
        return responseUtil.success("Account Disabled successfully", null);
    }

    public ResponseEntity<ApiResponse> deleteAccount(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        user.setDeleted(true);
        userRepository.save(user);
        refreshTokenService.revokeForUser(user.getId());
        log.info("Account soft-deleted | email={}", email);
        return responseUtil.success("Account Deleted successfully", null);
    }

    public ResponseEntity<ApiResponse> updateStatus(String email, Status status) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        HoldPersonalDetails holdDetails = holdPersonalDetailsRepository.findByUser_EmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("Hold details not found for this user"));

        holdDetails.setStatus(status);
        holdPersonalDetailsRepository.save(holdDetails);

        String message;
        switch (status) {
            case VERIFIED -> message = "User details successfully verified.";
            case PENDING -> message = "User details marked as pending approval.";
            case NOT_VERIFIED -> message = "User details rejected.";
            default -> message = "User status updated.";
        }

        log.info("User hold details status updated | email={} | status={}", email, status);
        return responseUtil.success(message, holdDetails);
    }

    public ResponseEntity<ApiResponse> updateAddHolderStatus(String email, Status status) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        AddHolderDetails addHolderDetails = addHolderDetailsRepository.findByUser_EmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("Address holder details not found for this user"));

        Status previousStatus = addHolderDetails.getStatus();
        if (status == Status.VERIFIED
                && previousStatus != Status.PENDING
                && previousStatus != Status.VERIFIED) {
            return responseUtil.badRequest(
                    "This KYC record is not awaiting Tier 3 address approval.",
                    Map.of("status", previousStatus == null ? null : previousStatus.name())
            );
        }

        addHolderDetails.setStatus(status);

        String message;
        if (status == Status.VERIFIED) {
            user.setTier("3");

            String verifiedAddress = validatorUitls.firstNonBlank(addHolderDetails.getAddress(), user.getAddress());
            if (verifiedAddress != null) {
                user.setAddress(verifiedAddress);
            }

            userRepository.save(user);

            if (user.getClientId() != null && !user.getClientId().isBlank()) {
                try {
                    PatchClientRequest patchRequest = buildTier3AddressPatchRequest(user, addHolderDetails.getAddress());
                    ClientResponse patchResponse = externalAPIs.updatePatchClient(user.getClientId(), patchRequest);
                    if (patchResponse == null) {
                        log.warn("Tier 3 address patch returned null for clientId={}", user.getClientId());
                    }
                } catch (Exception ex) {
                    // Do not fail admin approval flow when external sync fails.
                    log.warn("Tier 3 address patch failed for clientId={} | reason={}",
                            user.getClientId(), ex.getMessage());
                }
            }

            message = "Address verification approved. User upgraded to Tier 3.";
        } else if (status == Status.PENDING) {
            message = "Address verification marked as pending approval.";
        } else if (status == Status.NOT_VERIFIED) {
            message = "Address verification request declined.";
        } else {
            message = "Address verification status updated.";
        }

        addHolderDetailsRepository.save(addHolderDetails);

        return responseUtil.success(message, null);
    }


    public ResponseEntity<ApiResponse> saveDeviceDetails(String email, DeviceDetailsRequest request) {
        return deviceDetailsService.addDeviceDetails(email, request);
    }

    public ResponseEntity<ApiResponse> registerBiometric(String email, BiometricRegisterRequest request) {
        return deviceDetailsService.registerBiometric(email, request);
    }

    public ResponseEntity<ApiResponse> validateBiometric(String email, BiometricValidateRequest request) {
        return deviceDetailsService.validatePublicKey(email, request);
    }

    public ResponseEntity<ApiResponse> saveFcmToken(String email, String fcmToken) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found with email: " + email));

        Optional<FCMToken> existingTokenOpt = fcmTokenRepository.findByUserId(user.getId());

        FCMToken tokenEntity;
        if (existingTokenOpt.isPresent()) {
            tokenEntity = existingTokenOpt.get();
            tokenEntity.setToken(fcmToken); // Update existing token
        } else {
            tokenEntity = new FCMToken();
            tokenEntity.setUser(user);
            tokenEntity.setToken(fcmToken);
        }

        fcmTokenRepository.save(tokenEntity);

        return responseUtil.success("FCM Token saved successfully", null);
    }


    public ResponseEntity<ApiResponse> editUserIdentifiers(EditUserIdentifiersRequest request, String currentEmail) {
        User user = userRepository.findByEmailAddress(normalizeEmail(currentEmail))
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        if (request.clientId() == null || request.clientId().isBlank()) {
            return responseUtil.badRequest("clientId is required", null);
        }

        user.setClientId(request.clientId());
        user.setInvestmentAccountId(request.investmentAccountId());
        userRepository.save(user);

        return responseUtil.success("User identifiers updated successfully", user);
    }

    public ResponseEntity<ApiResponse> updateUser(UpdateUser request) {
        if (!StringUtils.hasText(request.getEmail())) {
            return responseUtil.badRequest("Sorry", null);
        }
        User user = userRepository.findByEmailAddress(normalizeEmail(request.getEmail()))
                .orElseThrow(() -> new ResourceNotFound("Sorry"));

        boolean updated = false;

        if (request.getAccountNumber() != null) {
            if (request.getAccountNumber().isBlank()) {
                return responseUtil.badRequest("Sorry", null);
            }
            user.setAccountNumber(request.getAccountNumber().trim());
            updated = true;
        }
        if (request.getNin() != null) {
            if (request.getNin().isBlank()) {
                return responseUtil.badRequest("Sorry", null);
            }
            user.setNin(request.getNin().trim());
            updated = true;
        }
        if (request.getBvn() != null) {
            if (request.getBvn().isBlank()) {
                return responseUtil.badRequest("Sorry", null);
            }
            user.setBvn(request.getBvn().trim());
            updated = true;
        }

        if (!updated) {
            return responseUtil.badRequest("Sorry", null);
        }

        userRepository.save(user);

        return responseUtil.success("User updated successfully", null);
    }

    private String maskBvn(String bvn) {
        if (bvn == null || bvn.length() < 4) return "****";
        return bvn.substring(0, 2) + "****" + bvn.substring(bvn.length() - 2);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim();
        try {
            normalized = java.net.URLDecoder.decode(normalized, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
        }
        return normalized.trim().toLowerCase(Locale.ROOT);
    }

    private void retirePreviousOtps(String email) {
        List<UserOtp> previous = otpRepository.findAllByEmailIgnoreCase(email);
        if (previous.isEmpty()) {
            return;
        }
        otpRepository.deleteAll(previous);
        log.debug("Retired {} superseded OTP(s) | email={}", previous.size(), email);
    }

    private boolean isTierAtLeast(String tier, int minimumTier) {
        if (tier == null || tier.isBlank()) {
            return false;
        }
        try {
            return Integer.parseInt(tier.trim()) >= minimumTier;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String safeToJson(Object object) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(object);
        } catch (Exception e) {
            return "[unserializable]";
        }
    }

    private ResponseEntity<ApiResponse> badRequest(String message) {
        return responseUtil.badRequest(message, null);
    }

    private ResponseEntity<ApiResponse> unauthorized(String message) {
        return responseUtil.unauthorized(message, null);
    }

    private ResponseEntity<ApiResponse> serverError(String message) {
        return responseUtil.serverError(message, null);
    }

}
