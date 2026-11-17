package com.myrtle.middleware.utils;

import com.myrtle.middleware.dtos.request.NextOfKinRequest;
import com.myrtle.middleware.dtos.response.FinIdLookupResponse;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.model.BvnCache;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.repository.BvnCacheRepo;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.nextofkin.NextOfKinService;
import com.myrtle.middleware.services.third_party.ExternalAPIs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignupValidations {
    public final UserRepository userRepository;
    public final OtpRepository otpRepository;
    public final ExternalAPIs externalAPIs;
    public final BvnCheckService bvnCheckService;
    public final NextOfKinService nextOfKinService;
    private final BvnCacheRepo bvnCacheRepo;

    public void validateNok(List<NextOfKinRequest> nextOfKins) {
        if (nextOfKins != null && !nextOfKins.isEmpty()) {
            try {
                nextOfKinService.validateSignupNextOfKins(nextOfKins);
            } catch (IllegalArgumentException ex) {
                log.info("Error validating next of Kin for new user.Error: {}", ex.getMessage());
                throw new CompletionException("Error validating your next of kins", null);
            }
        }
    }

    public void checkIfUserExists(String email) {
        if (userRepository.findByEmailAddress(email).isPresent()) {
            throw new CompletionException("Account already exists. Please login to continue", null);
        }
    }

    public void checkIfUserExistsOnMyWealth(String bvn) {
        FinIdLookupResponse response = externalAPIs.lookupClientByFinId(bvn);

        if (response.isFound()) {
            log.warn("BVN already exists on another account.");
            throw new CompletionException("BVN already exists on another account. " +
                    "Kindly login with your username and password.", null);
        }

        if (bvn == null
                || bvn.isBlank()
                || "UNVERIFIED".equalsIgnoreCase(bvn)) {
            throw new CompletionException("BVN must be provided and verified before account creation.", null);
        }

        Optional<User> existingBvnUser = userRepository.findByBvn(bvn);
        if (existingBvnUser.isPresent()) {
            throw new CompletionException("BVN already exists on another account. kindly login with your username and password ", null);
        }
    }

    public void checkIfOtpValidated(String otpReference, String email) {
        Optional<UserOtp> userOtpOptional = otpRepository.findOtpByOtpReferenceAndEmail(otpReference, email);
        if (userOtpOptional.isEmpty() || !userOtpOptional.get().isVerified()) {
            throw new CompletionException("OTP not verified. Please verify your email before creating an account.", null);
        }
    }

    public BvnCache validateBvn(String bvn) {

        BvnCache bvnCache = bvnCacheRepo.findBvnCacheByBvn(bvn);
        if (bvnCache == null) {
            log.info("BVN has not been prevalidated");
            throw new FunctionException("BVN validation is required for account creation", null);
        }
        if (!bvnCache.isVerified()) {
            log.info("BVN has not been verified");
            throw new FunctionException("Your records does not match the BVNs supplied", null);
        }
        return bvnCache;
    }
}
