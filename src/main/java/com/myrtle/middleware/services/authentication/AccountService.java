package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.AddHoldersDetailsDto;
import com.myrtle.middleware.dtos.bvn.BVNRequest;
import com.myrtle.middleware.dtos.request.CreateAccountRequest;
import com.myrtle.middleware.dtos.request.EditUserRequest;
import com.myrtle.middleware.dtos.request.HoldPersonalDetailsDto;
import com.myrtle.middleware.dtos.request.NextOfKinRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.InvestmentAccountResponse2;
import com.myrtle.middleware.dtos.response.MyWealthClientResponse;
import com.myrtle.middleware.dtos.response.TokenResponse;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.enums.Status;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.exceptions.UpstreamServiceException;
import com.myrtle.middleware.model.*;
import com.myrtle.middleware.repository.*;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.services.nextofkin.NextOfKinService;
import com.myrtle.middleware.services.questionnaire.QuestionnaireService;
import com.myrtle.middleware.services.refer.ReferralService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import com.myrtle.middleware.utils.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {
    private final JwtUtils jwtUtil;
    private final AuthService authService;
    private final ResponseUtil responseUtil;
    private final ExternalAPIs externalAPIs;
    private final EmailService emailService;
    private final OtpRepository otpRepository;
    private final PersistUser persistUserUtil;
    private final ValidatorUitls validatorUitls;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final BvnCheckService bvnCheckService;
    private final NextOfKinService nextOfKinService;
    private final SignupValidations signupValidations;
    private final HoldPersonalDetailsRepository holdPersonalDetailsRepository;
    private final AddHolderDetailsRepository addHolderDetailsRepository;
    private final QuestionnaireService questionnaireService;
    private final NextOfKinRepository nextOfKinRepository;
    private final ReferralService referralService;

    @Lazy
    @Autowired
    private PendingOnboardingService pendingOnboardingService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private final String CUSTOMER_CARE_EMAIL = "wecare@myrtleng.com";

    @Value("${mywealth.branch.id}")
    private String branchId;

    /**
     * Renders a possibly-null profile field as an empty string rather than the literal "null".
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private String resolveInvestmentAccountId(String clientId, String email) {
        List<InvestmentAccountResponse2> accounts = externalAPIs.getInvestmentAccounts(clientId);
        if (accounts == null || accounts.isEmpty()) {
            log.error("Account creation aborted: no investment account found after client creation | email={} | clientId={}", email, clientId);
            throw new UpstreamServiceException("MyWealth",
                    "Your account was created, but we could not confirm your investment account. Please try again shortly.", null);
        }
        return accounts.getFirst().getId();
    }

    public BVNRequest buildRequestFromUser(User user) {
        return new BVNRequest(user.getBvn(), user.getFirstName(),
                user.getLastName(), user.getPhoneNumber(), user.getEmailAddress(),
                user.getGender(), true);
    }

    private void awaitAll(CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(futures).join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeCause) throw runtimeCause;
            if (cause instanceof Error error) throw error;
            throw e;
        }
    }

    public ResponseEntity<ApiResponse> doAccountCreation(CreateAccountRequest request) {
        String otpReference = request.otpReference();
        List<NextOfKinRequest> nextOfKins = request.nextOfKins();
        String agentId = referralService.resolveAgentId(request.inviteCode());
        String emailAddress = normalizeEmail(request.emailAddress());

        log.info("Account creation started | email={} | nokCount={}", emailAddress, nextOfKins != null ? nextOfKins.size() : 0);

        CompletableFuture<Void> validateNokFuture = CompletableFuture.runAsync(() -> signupValidations.validateNok(nextOfKins));
        CompletableFuture<Void> userExistsFuture = CompletableFuture.runAsync(() -> signupValidations.checkIfUserExists(emailAddress));
        CompletableFuture<Void> myWealthFuture = CompletableFuture.runAsync(() -> signupValidations.checkIfUserExistsOnMyWealth(request.bvn()));
        CompletableFuture<Void> otpFuture = CompletableFuture.runAsync(() -> signupValidations.checkIfOtpValidated(otpReference, emailAddress));
        awaitAll(validateNokFuture, userExistsFuture, myWealthFuture, otpFuture);
        log.debug("Signup pre-validations passed | email={}", emailAddress);

        BvnCache bvnCache = signupValidations.validateBvn(request.bvn());

        User user = instantiateUser(request);

        bvnCheckService.bvnValidationAlgorithm(authService.toQoreIdResponse(bvnCache), buildRequestFromUser(user));

        log.debug("BVN validated | email={}", user.getEmailAddress());

        MyWealthClientResponse clientResponse = externalAPIs.createIndividualClient(user, bvnCache, true, agentId);
        if (clientResponse == null || clientResponse.id() == null) {
            log.error("Account creation aborted: MyWealth createIndividualClient returned no client id | email={}", user.getEmailAddress());
            throw new UpstreamServiceException("MyWealth", "An error occurred while creating your account. Please try again shortly.", null);
        }
        log.info("MyWealth client created | email={} | clientId={} | status={}",
                user.getEmailAddress(), clientResponse.id(), clientResponse.status());

        String investmentAccountId = resolveInvestmentAccountId(clientResponse.id(), user.getEmailAddress());
        log.info("Investment account resolved | email={} | investmentAccountId={}", user.getEmailAddress(), investmentAccountId);

        User savedUser = persistUserUtil.persistUser(user, nextOfKins, clientResponse.id(), investmentAccountId, branchId, Role.USER);
        log.info("Account creation completed successfully | email={} | clientId={}", savedUser.getEmailAddress(), clientResponse.id());

        if (pendingOnboardingService != null) {
            pendingOnboardingService.markAccountCreated(
                    savedUser.getEmailAddress(), PendingOnboarding.Type.INDIVIDUAL, savedUser.getId());
        }

        try {
            referralService.recordReferral(agentId, savedUser.getId(), "INDIVIDUAL");
        } catch (Exception ex) {
            log.warn("recordReferral failed for user {}: {}", savedUser.getEmailAddress(), ex.getMessage());
        }

        try {
            emailService.sendWelcomeEmail(savedUser);
            emailService.notifyCustomerServiceForNewUser(savedUser, nextOfKins);
        } catch (Exception ex) {
            log.warn("Welcome email delivery failed for user {}: {}", savedUser.getEmailAddress(), ex.getMessage());
        }

        String token = jwtUtil.generateToken(savedUser.getEmailAddress());
        TokenResponse tokenResponse = new TokenResponse(token, refreshTokenService.issueForUser(savedUser));

        savedUser.setMiddleName(validatorUitls.firstNonBlank(bvnCache.getMiddleName(), savedUser.getMiddleName()));
        savedUser.setGender(validatorUitls.normalizeGenderValue(validatorUitls.firstNonBlank(bvnCache.getGender(), savedUser.getGender())));
        savedUser.setAddress(validatorUitls.firstNonBlank(bvnCache.getResidentialAddress(), savedUser.getAddress()));
        userRepository.save(savedUser);

        return responseUtil.success("Account created successfully", tokenResponse);
    }


    private User instantiateUser(CreateAccountRequest request) {
        User user = new User();
        user.setFirstName(request.firstName());
        user.setLastName(request.lastName());
        user.setMiddleName(request.middleName());
        user.setEmailAddress(request.emailAddress());
        user.setPhoneNumber(request.phoneNumber());
        user.setPassword(request.password());
        user.setBvn(request.bvn());
        user.setAddress(request.address());
        user.setGender(request.gender());
        user.setPep(request.isPep());
        user.setPepPosition(request.pepPosition());
        user.setEmailAddress(normalizeEmail(user.getEmailAddress()));
        return user;
    }

    public void assertCorporateUserCanBeCreated(User user, List<NextOfKinRequest> nextOfKins) {
        user.setEmailAddress(normalizeEmail(user.getEmailAddress()));

        if (nextOfKins != null && !nextOfKins.isEmpty()) {
            try {
                nextOfKinService.validateSignupNextOfKins(nextOfKins);
            } catch (IllegalArgumentException ex) {
                throw new FunctionException(ex.getMessage(), null);
            }
        }

        if (userRepository.findByEmailAddress(user.getEmailAddress()).isPresent()) {
            throw new FunctionException("Account already exists", null);
        }

        if (user.getBvn() == null || user.getBvn().isBlank()) {
            throw new FunctionException("BVN must be provided and verified before account creation.", null);
        }

        if (userRepository.findByBvn(user.getBvn()).isPresent()) {
            throw new FunctionException("BVN already exists on another account.", null);
        }

        Optional<UserOtp> userOtp =
                otpRepository.findFirstByEmailIgnoreCaseOrderByTimeGeneratedDesc(user.getEmailAddress());

        if (userOtp.isEmpty() || !userOtp.get().isVerified()) {
            throw new FunctionException(
                    "OTP not verified. Please verify your email before creating an account.", null);
        }
        if (!userOtp.get().isVerificationFresh()) {
            throw new FunctionException(
                    "Your email verification has expired. Please verify your email again to continue.", null);
        }
    }

    @Transactional
    public User createLocalCorporateUser(
            User user, List<NextOfKinRequest> nextOfKins, String clientId, String investmentId) {

        // Re-checked here rather than assumed: this is the single choke point for corporate logins,
        // so it stays safe for any future caller even though createCorporateAccount pre-flights it.
        assertCorporateUserCanBeCreated(user, nextOfKins);

        User savedUser = persistUserUtil.persistUser(user, nextOfKins, clientId, investmentId, branchId, Role.CORPORATE_USER);

        // Corporate holders are tier 3 by default - the company itself passed full KYB.
        savedUser.setTier("3");
        savedUser = userRepository.save(savedUser);

        try {
            emailService.sendWelcomeEmail(savedUser);
            emailService.notifyCustomerServiceForNewUser(savedUser, nextOfKins);
        } catch (Exception ex) {
            log.warn("Welcome email failed for corporate user {}: {}", savedUser.getEmailAddress(), ex.getMessage());
        }

        log.info("Corporate user created locally | email={} | clientId={} | investmentAccountId={}",
                savedUser.getEmailAddress(), clientId, investmentId);

        return savedUser;
    }

    public ResponseEntity<ApiResponse> addProfileImage(String email, String url) {

        Optional<User> optionalUser = userRepository.findByEmailAddress(email);

        if (optionalUser.isEmpty()) {
            return responseUtil.notFound("User not found", null);
        }

        if (url == null || url.trim().isEmpty()) {
            return responseUtil.badRequest("Profile image URL is required", null);
        }

        // Ensure URL points to an image
        if (!validatorUitls.isImageUrl(url)) {
            return responseUtil.badRequest("Invalid image format. Only image files are allowed", null);
        }

        User user = optionalUser.get();
        user.setProfileImage(url);
        userRepository.save(user);

        return responseUtil.success("Profile image updated successfully", null);
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

    public ResponseEntity<ApiResponse> editUserDetails(EditUserRequest editUserRequest, String email) {
        // 1. Find user
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not Found"));

        // 2. Find hold details if exists
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

        // 3. If no record found, create a new hold request
        HoldPersonalDetails holdPersonalDetails = new HoldPersonalDetails();
        holdPersonalDetails.setFirstName(editUserRequest.firstname());
        holdPersonalDetails.setMiddleName(editUserRequest.middleName() != null ? editUserRequest.middleName() : "");
        holdPersonalDetails.setLastName(editUserRequest.lastname());
        holdPersonalDetails.setProof_image(editUserRequest.imageProof());
        holdPersonalDetails.setPhoneNumber(editUserRequest.phoneNumber());
        holdPersonalDetails.setReason_for_change(editUserRequest.reason());
        holdPersonalDetails.setStatus(Status.PENDING);
        holdPersonalDetails.setUser(user); // important!

        holdPersonalDetailsRepository.save(holdPersonalDetails);

        // 4. Generate Token & Send mail to customer care
        String access_token = jwtUtil.generateToken(CUSTOMER_CARE_EMAIL);
        emailService.sendMailToCustomerCare(CUSTOMER_CARE_EMAIL, editUserRequest, access_token);

        // 5. Return success response
        return responseUtil.success("User details submitted for review and mail sent to customer care", holdPersonalDetails);
    }

}
