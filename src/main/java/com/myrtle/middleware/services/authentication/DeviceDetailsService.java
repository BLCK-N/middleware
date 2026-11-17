package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.request.BiometricRegisterRequest;
import com.myrtle.middleware.dtos.request.BiometricValidateRequest;
import com.myrtle.middleware.dtos.request.DeviceDetailsRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.BiometricCredentialResponse;
import com.myrtle.middleware.dtos.response.BiometricValidationResponse;
import com.myrtle.middleware.dtos.response.DeviceDetailsResponse;
import com.myrtle.middleware.enums.ActivationStatus;
import com.myrtle.middleware.enums.DevicePlatform;
import com.myrtle.middleware.exceptions.FunctionException;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.BiometricCredential;
import com.myrtle.middleware.model.DeviceDetails;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserOtp;
import com.myrtle.middleware.repository.BiometricCredentialRepository;
import com.myrtle.middleware.repository.DeviceDetailsRepository;
import com.myrtle.middleware.repository.OtpRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceDetailsService {

    private static final String DEFAULT_BIOMETRIC_ALGORITHM = "ES256";

    private final DeviceDetailsRepository deviceDetailsRepository;
    private final BiometricCredentialRepository biometricCredentialRepository;
    private final UserRepository userRepository;
    private final OtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResponseUtil responseUtil;

    /**
     * Creates or updates the authenticated user's single device record after a successful login.
     * Returns {@code null} when the login payload did not include a device object.
     */
    public DeviceDetailsResponse saveFromLogin(User user, DeviceDetailsRequest request) {
        if (request == null) {
            return null;
        }
        return DeviceDetailsResponse.from(upsert(user, request, true));
    }

    public ResponseEntity<ApiResponse> addDeviceDetails(String email, DeviceDetailsRequest request) {
        User user = findUser(email);
        requireVerifiedOtp(email, request == null ? null : request.otpReference());
        DeviceDetails saved = upsert(user, request, false);
        log.info("Device details saved | userId={} | deviceId={}", user.getId(), saved.getId());
        return responseUtil.success("Device details saved successfully", DeviceDetailsResponse.from(saved));
    }

    public ResponseEntity<ApiResponse> registerBiometric(String email, BiometricRegisterRequest request) {
        User user = findUser(email);
        DeviceDetails device = deviceDetailsRepository.findByIdAndUser_Id(request.deviceId(), user.getId())
                .orElseThrow(() -> new ResourceNotFound("Device not found"));

        if (device.getStatus() != ActivationStatus.ACTIVE) {
            throw new FunctionException("This device is not active. Sign in from the device again before enabling biometrics.", null);
        }

        String publicKey = requirePublicKey(request.publicKey());
        String algorithm = normalizeAlgorithm(request.algorithm());
        consumeOtp(email, request.otp(), request.otpReference());
        Instant now = Instant.now();

        BiometricCredential credential = biometricCredentialRepository.findByDevice_Id(device.getId())
                .orElseGet(BiometricCredential::new);

        boolean created = credential.getKeyId() == null;
        if (created) {
            credential.setDevice(device);
        }

        credential.setPublicKey(publicKey);
        credential.setAlgorithm(algorithm);
        credential.setStatus(ActivationStatus.ACTIVE);
        credential.setRegisteredAt(now);

        BiometricCredential saved = biometricCredentialRepository.save(credential);
        log.info(
                "Biometric public key {} | userId={} | deviceId={} | keyId={}",
                created ? "registered" : "rotated",
                user.getId(),
                device.getId(),
                saved.getKeyId()
        );

        return created
                ? responseUtil.created("Biometrics registered successfully", BiometricCredentialResponse.from(saved))
                : responseUtil.success("Biometrics registered successfully", BiometricCredentialResponse.from(saved));
    }

    public ResponseEntity<ApiResponse> validatePublicKey(String email, BiometricValidateRequest request) {
        User user = findUser(email);
        String publicKey = requirePublicKey(request.publicKey());
        DeviceDetails device = resolveDeviceForValidation(user.getId(), request.deviceId());
        if (device == null || device.getStatus() != ActivationStatus.ACTIVE) {
            return publicKeyNotForUser();
        }

        BiometricCredential credential = biometricCredentialRepository.findByDevice_Id(device.getId())
                .orElse(null);
        if (credential == null
                || credential.getStatus() != ActivationStatus.ACTIVE
                || !publicKeysMatch(credential.getPublicKey(), publicKey)) {
            return publicKeyNotForUser();
        }

        log.info("Biometric public key validated | userId={} | deviceId={} | keyId={}",
                user.getId(), device.getId(), credential.getKeyId());
        return responseUtil.success(
                "Public key is registered for this user",
                new BiometricValidationResponse(
                        true,
                        device.getId(),
                        credential.getKeyId(),
                        credential.getAlgorithm(),
                        credential.getStatus()
                )
        );
    }

    private DeviceDetails upsert(User user, DeviceDetailsRequest request, boolean fromLogin) {
        String clientDeviceId = requireClientDeviceId(request);
        DevicePlatform platform = parsePlatform(request == null ? null : request.platform());
        Instant now = Instant.now();

        DeviceDetails device = deviceDetailsRepository.findByUser_Id(user.getId())
                .orElseGet(DeviceDetails::new);

        boolean created = device.getId() == null;
        device.setUser(user);
        if (created) {
            device.setFirstSeenAt(now);
        } else if (device.getClientDeviceId() != null && !device.getClientDeviceId().equals(clientDeviceId)) {
            revokeBiometric(device);
        }

        device.setClientDeviceId(clientDeviceId);
        if (request != null && StringUtils.hasText(request.deviceName())) {
            device.setDeviceName(request.deviceName().trim());
        }
        if (platform != null) {
            device.setPlatform(platform);
        }
        if (request != null && StringUtils.hasText(request.appVersion())) {
            device.setAppVersion(request.appVersion().trim());
        }
        device.setStatus(ActivationStatus.ACTIVE);
        device.setLastSeenAt(now);
        if (fromLogin) {
            device.setLastLoginAt(now);
        }

        DeviceDetails saved = deviceDetailsRepository.save(device);
        log.info(
                "Device details {} | userId={} | deviceId={} | clientDeviceId={} | fromLogin={}",
                created ? "created" : "updated",
                user.getId(),
                saved.getId(),
                saved.getClientDeviceId(),
                fromLogin
        );
        return saved;
    }

    private void revokeBiometric(DeviceDetails device) {
        biometricCredentialRepository.findByDevice_Id(device.getId()).ifPresent(credential -> {
            credential.setStatus(ActivationStatus.REVOKED);
            biometricCredentialRepository.save(credential);
            log.info("Biometric credential revoked after device change | deviceId={} | keyId={}",
                    device.getId(), credential.getKeyId());
        });
    }

    private DeviceDetails resolveDeviceForValidation(String userId, String deviceId) {
        if (StringUtils.hasText(deviceId)) {
            return deviceDetailsRepository.findByIdAndUser_Id(deviceId.trim(), userId).orElse(null);
        }
        return deviceDetailsRepository.findByUser_Id(userId).orElse(null);
    }

    private ResponseEntity<ApiResponse> publicKeyNotForUser() {
        return responseUtil.forbidden(
                "This public key is not registered for this user",
                new BiometricValidationResponse(false, null, null, null, null)
        );
    }

    private boolean publicKeysMatch(String stored, String provided) {
        if (!StringUtils.hasText(stored) || !StringUtils.hasText(provided)) {
            return false;
        }
        byte[] storedBytes = stored.trim().getBytes(StandardCharsets.US_ASCII);
        byte[] providedBytes = provided.trim().getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(storedBytes, providedBytes);
    }

    private void requireVerifiedOtp(String email, String otpReference) {
        if (!StringUtils.hasText(otpReference)) {
            throw new FunctionException("OTP must be verified before binding a device to this account.", null);
        }

        UserOtp userOtp = otpRepository.findOtpByOtpReferenceAndEmail(otpReference.trim(), email)
                .orElseThrow(() -> new FunctionException(
                        "OTP must be verified before binding a device to this account.", null));

        if (!userOtp.isVerified()) {
            throw new FunctionException("OTP must be verified before binding a device to this account.", null);
        }
        if (!userOtp.isVerificationFresh()) {
            throw new FunctionException(
                    "Your OTP verification has expired. Please verify your email again to continue.", null);
        }
    }

    /**
     * Validates the OTP in this same request (unlike device-details, which expects a prior
     * {@code /verify-otp}). Marks the OTP used so it cannot be replayed to rotate the key again.
     */
    private void consumeOtp(String email, String otp, String otpReference) {
        if (!StringUtils.hasText(otp) || !StringUtils.hasText(otpReference)) {
            throw new FunctionException("OTP and otpReference are required to update biometrics.", null);
        }

        UserOtp userOtp = otpRepository.findOtpByOtpReferenceAndEmail(otpReference.trim(), email)
                .orElseThrow(() -> new FunctionException("OTP not found for this email", null));

        if (userOtp.isVerified()) {
            throw new FunctionException("OTP already verified", null);
        }
        if (userOtp.isExpired()) {
            throw new FunctionException("OTP expired. generate an OTP to continue", null);
        }
        if (!passwordEncoder.matches(otp, userOtp.getOtp())) {
            throw new FunctionException("Invalid OTP", null);
        }

        userOtp.markVerified();
        otpRepository.save(userOtp);
    }

    private User findUser(String email) {
        return userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found"));
    }

    private String requireClientDeviceId(DeviceDetailsRequest request) {
        if (request == null || !StringUtils.hasText(request.deviceId())) {
            throw new FunctionException("Device ID is required", null);
        }
        return request.deviceId().trim();
    }

    private String requirePublicKey(String publicKey) {
        if (!StringUtils.hasText(publicKey)) {
            throw new FunctionException("Public key is required", null);
        }
        return publicKey.trim();
    }

    private DevicePlatform parsePlatform(String platform) {
        if (!StringUtils.hasText(platform)) {
            return null;
        }
        try {
            return DevicePlatform.from(platform);
        } catch (IllegalArgumentException ex) {
            throw new FunctionException("Platform must be IOS, ANDROID, or WEB", null);
        }
    }

    private String normalizeAlgorithm(String algorithm) {
        String normalized = StringUtils.hasText(algorithm)
                ? algorithm.trim().toUpperCase(Locale.ROOT)
                : DEFAULT_BIOMETRIC_ALGORITHM;
        if (!DEFAULT_BIOMETRIC_ALGORITHM.equals(normalized)) {
            throw new FunctionException("Unsupported biometric algorithm. Use ES256.", null);
        }
        return normalized;
    }
}
