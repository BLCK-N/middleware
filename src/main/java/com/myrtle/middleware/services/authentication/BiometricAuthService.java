package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.enums.DevicePlatform;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserBiometricDevice;
import com.myrtle.middleware.repository.UserBiometricDeviceRepository;
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
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BiometricAuthService {

    private final UserRepository userRepository;
    private final UserBiometricDeviceRepository userBiometricDeviceRepository;
    private final ResponseUtil responseUtil;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ResponseEntity<com.myrtle.middleware.dtos.response.ApiResponse> enableForUser(String email, String deviceId, String deviceType) {
        User user = userRepository.findByEmailAddress(normalizeEmail(email)).orElse(null);
        if (user == null) {
            return responseUtil.notFound("User not found", null);
        }

        DevicePlatform platform = parsePlatform(deviceType);
        if (!StringUtils.hasText(deviceId) || platform == null) {
            return responseUtil.badRequest("X-Device-Id and X-Device-Type headers are required for biometric registration.", null);
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        UserBiometricDevice device = userBiometricDeviceRepository.findByUser_IdAndDeviceId(user.getId(), deviceId.trim())
                .orElseGet(UserBiometricDevice::new);

        device.setUser(user);
        device.setDeviceId(deviceId.trim());
        device.setDeviceType(platform);
        device.setBioTokenHash(tokenHash);
        device.setExpiresAt(Instant.now().plusSeconds(30L * 24L * 60L * 60L));
        device.setRevokedAt(null);
        device.setLastUsedAt(null);
        userBiometricDeviceRepository.save(device);

        log.info("Biometric enabled | userId={} | deviceId={} | deviceType={}", user.getId(), deviceId, platform);
        return responseUtil.success("Biometric registration successful", Map.of(
                "bio_token", rawToken,
                "expires_in", 30L * 24L * 60L * 60L
        ));
    }

    public ResponseEntity<com.myrtle.middleware.dtos.response.ApiResponse> revokeForUser(String email, String deviceId, String deviceType) {
        User user = userRepository.findByEmailAddress(normalizeEmail(email)).orElse(null);
        if (user == null) {
            return responseUtil.notFound("User not found", null);
        }
        if (!StringUtils.hasText(deviceId)) {
            return responseUtil.badRequest("X-Device-Id header is required for biometric revocation.", null);
        }

        DevicePlatform platform = parsePlatform(deviceType);
        UserBiometricDevice device = userBiometricDeviceRepository.findByUser_IdAndDeviceId(user.getId(), deviceId.trim())
                .orElse(null);

        if (device == null || (platform != null && device.getDeviceType() != platform)) {
            return responseUtil.badRequest("No active biometric registration was found for this device.", null);
        }

        device.setRevokedAt(Instant.now());
        device.setExpiresAt(Instant.now());
        userBiometricDeviceRepository.save(device);
        log.info("Biometric revoked | userId={} | deviceId={} | deviceType={}", user.getId(), deviceId, device.getDeviceType());
        return responseUtil.success("Biometric device revoked successfully", null);
    }

    public User validateBioToken(String email, String bioToken, String deviceId, String deviceType) {
        if (!StringUtils.hasText(email) || !StringUtils.hasText(bioToken)) {
            return null;
        }

        User user = userRepository.findByEmailAddress(normalizeEmail(email)).orElse(null);
        if (user == null) {
            return null;
        }

        if (!StringUtils.hasText(deviceId)) {
            return null;
        }

        DevicePlatform platform = parsePlatform(deviceType);
        if (platform == null) {
            return null;
        }

        UserBiometricDevice biometricDevice = userBiometricDeviceRepository.findByBioTokenHash(hashToken(bioToken.trim())).orElse(null);
        if (biometricDevice == null || !biometricDevice.getUser().getId().equals(user.getId())) {
            return null;
        }
        if (!biometricDevice.getDeviceId().equals(deviceId.trim()) || biometricDevice.getDeviceType() != platform) {
            return null;
        }
        if (biometricDevice.isRevoked() || biometricDevice.isExpired()) {
            return null;
        }

        biometricDevice.setLastUsedAt(Instant.now());
        userBiometricDeviceRepository.save(biometricDevice);
        return user;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private DevicePlatform parsePlatform(String deviceType) {
        if (!StringUtils.hasText(deviceType)) {
            return null;
        }
        try {
            return DevicePlatform.valueOf(deviceType.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
