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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceDetailsServiceTest {

    @Mock private DeviceDetailsRepository deviceDetailsRepository;
    @Mock private BiometricCredentialRepository biometricCredentialRepository;
    @Mock private UserRepository userRepository;
    @Mock private OtpRepository otpRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private DeviceDetailsService service;

    @BeforeEach
    void setUp() {
        service = new DeviceDetailsService(
                deviceDetailsRepository,
                biometricCredentialRepository,
                userRepository,
                otpRepository,
                passwordEncoder,
                new ResponseUtil()
        );
    }

    @Test
    void saveFromLoginReturnsNullWhenDeviceIsOmitted() {
        assertThat(service.saveFromLogin(user(), null)).isNull();
        verify(deviceDetailsRepository, never()).save(any());
    }

    @Test
    void saveFromLoginCreatesDeviceForAuthenticatedUser() {
        User user = user();
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.empty());
        when(deviceDetailsRepository.save(any(DeviceDetails.class))).thenAnswer(invocation -> {
            DeviceDetails device = invocation.getArgument(0);
            device.setId("device-uuid");
            return device;
        });

        DeviceDetailsResponse saved = service.saveFromLogin(user, deviceRequest());

        assertThat(saved.id()).isEqualTo("device-uuid");
        assertThat(saved.userId()).isEqualTo("user-id");
        assertThat(saved.clientDeviceId()).isEqualTo("ios-install-abc-123");
        assertThat(saved.deviceName()).isEqualTo("David's iPhone");
        assertThat(saved.platform()).isEqualTo(DevicePlatform.IOS);
        assertThat(saved.appVersion()).isEqualTo("1.2.0");
        assertThat(saved.status()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(saved.firstSeenAt()).isNotNull();
        assertThat(saved.lastSeenAt()).isNotNull();
        assertThat(saved.lastLoginAt()).isNotNull();
        verify(deviceDetailsRepository).save(any(DeviceDetails.class));
    }

    @Test
    void saveFromLoginUpdatesExistingDeviceAndTouchesLastLogin() {
        User user = user();
        Instant firstSeen = Instant.parse("2026-09-01T08:00:00Z");
        DeviceDetails existing = existingDevice(user, firstSeen);
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.of(existing));
        when(deviceDetailsRepository.save(any(DeviceDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceDetailsResponse saved = service.saveFromLogin(
                user,
                new DeviceDetailsRequest("ios-install-abc-123", "David's iPhone", "IOS", "1.3.0")
        );

        assertThat(saved.id()).isEqualTo("device-uuid");
        assertThat(saved.appVersion()).isEqualTo("1.3.0");
        assertThat(saved.firstSeenAt()).isEqualTo(firstSeen);
        assertThat(saved.lastLoginAt()).isNotNull();
        assertThat(saved.lastSeenAt()).isNotNull();
        verify(biometricCredentialRepository, never()).save(any());
    }

    @Test
    void saveFromLoginRevokesBiometricWhenClientDeviceIdChanges() {
        User user = user();
        DeviceDetails existing = existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"));
        BiometricCredential credential = activeCredential(existing);
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.of(existing));
        when(deviceDetailsRepository.save(any(DeviceDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(biometricCredentialRepository.findByDevice_Id("device-uuid")).thenReturn(Optional.of(credential));
        when(biometricCredentialRepository.save(any(BiometricCredential.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.saveFromLogin(user, new DeviceDetailsRequest("android-install-xyz", "Pixel", "ANDROID", "2.0.0"));

        ArgumentCaptor<BiometricCredential> captor = ArgumentCaptor.forClass(BiometricCredential.class);
        verify(biometricCredentialRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ActivationStatus.REVOKED);
    }

    @Test
    void addDeviceDetailsCreatesRecordWithoutLastLogin() {
        User user = user();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref", "user@example.com"))
                .thenReturn(Optional.of(verifiedOtp()));
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.empty());
        when(deviceDetailsRepository.save(any(DeviceDetails.class))).thenAnswer(invocation -> {
            DeviceDetails device = invocation.getArgument(0);
            device.setId("device-uuid");
            return device;
        });

        ResponseEntity<ApiResponse> response = service.addDeviceDetails("user@example.com", deviceRequest());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DeviceDetailsResponse saved = (DeviceDetailsResponse) response.getBody().getData();
        assertThat(saved.clientDeviceId()).isEqualTo("ios-install-abc-123");
        assertThat(saved.lastLoginAt()).isNull();
        assertThat(saved.lastSeenAt()).isNotNull();
    }

    @Test
    void addDeviceDetailsRejectsUnknownPlatform() {
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user()));
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref", "user@example.com"))
                .thenReturn(Optional.of(verifiedOtp()));

        assertThatThrownBy(() -> service.addDeviceDetails(
                "user@example.com",
                new DeviceDetailsRequest("ios-install-abc-123", "Phone", "WINDOWS", "1.0.0", "otp-ref")
        )).isInstanceOf(FunctionException.class)
                .hasMessage("Platform must be IOS, ANDROID, or WEB");
    }

    @Test
    void addDeviceDetailsRejectsMissingOtp() {
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user()));

        assertThatThrownBy(() -> service.addDeviceDetails(
                "user@example.com",
                new DeviceDetailsRequest("ios-install-abc-123", "David's iPhone", "IOS", "1.2.0")
        )).isInstanceOf(FunctionException.class)
                .hasMessage("OTP must be verified before binding a device to this account.");
        verify(deviceDetailsRepository, never()).save(any());
    }

    @Test
    void addDeviceDetailsRejectsUnverifiedOtp() {
        UserOtp otp = new UserOtp();
        otp.setEmail("user@example.com");
        otp.setOtpReference("otp-ref");
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user()));
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref", "user@example.com"))
                .thenReturn(Optional.of(otp));

        assertThatThrownBy(() -> service.addDeviceDetails("user@example.com", deviceRequest()))
                .isInstanceOf(FunctionException.class)
                .hasMessage("OTP must be verified before binding a device to this account.");
        verify(deviceDetailsRepository, never()).save(any());
    }

    @Test
    void registerBiometricSavesPublicKeyAgainstTheUsersDevice() {
        User user = user();
        DeviceDetails device = existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"));
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref", "user@example.com"))
                .thenReturn(Optional.of(pendingOtp()));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(true);
        when(otpRepository.save(any(UserOtp.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceDetailsRepository.findByIdAndUser_Id("device-uuid", "user-id")).thenReturn(Optional.of(device));
        when(biometricCredentialRepository.findByDevice_Id("device-uuid")).thenReturn(Optional.empty());
        when(biometricCredentialRepository.save(any(BiometricCredential.class))).thenAnswer(invocation -> {
            BiometricCredential credential = invocation.getArgument(0);
            credential.setKeyId("key-uuid");
            return credential;
        });

        ResponseEntity<ApiResponse> response = service.registerBiometric(
                "user@example.com",
                biometricRequest("device-uuid", "BASE64URL_ENCODED_PUBLIC_KEY", "ES256")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getMessage()).isEqualTo("Biometrics registered successfully");
        BiometricCredentialResponse saved = (BiometricCredentialResponse) response.getBody().getData();
        assertThat(saved.deviceId()).isEqualTo("device-uuid");
        assertThat(saved.keyId()).isEqualTo("key-uuid");
        assertThat(saved.publicKey()).isEqualTo("BASE64URL_ENCODED_PUBLIC_KEY");
        assertThat(saved.algorithm()).isEqualTo("ES256");
        assertThat(saved.status()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(saved.registeredAt()).isNotNull();

        ArgumentCaptor<UserOtp> otpCaptor = ArgumentCaptor.forClass(UserOtp.class);
        verify(otpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().isVerified()).isTrue();
    }

    @Test
    void registerBiometricRejectsInvalidOtpBeforeSaving() {
        User user = user();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(deviceDetailsRepository.findByIdAndUser_Id("device-uuid", "user-id"))
                .thenReturn(Optional.of(existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"))));
        when(otpRepository.findOtpByOtpReferenceAndEmail("otp-ref", "user@example.com"))
                .thenReturn(Optional.of(pendingOtp()));
        when(passwordEncoder.matches("123456", "hashed-otp")).thenReturn(false);

        assertThatThrownBy(() -> service.registerBiometric(
                "user@example.com",
                biometricRequest("device-uuid", "BASE64URL_ENCODED_PUBLIC_KEY", "ES256")
        )).isInstanceOf(FunctionException.class)
                .hasMessage("Invalid OTP");
        verify(biometricCredentialRepository, never()).save(any());
        verify(otpRepository, never()).save(any());
    }

    @Test
    void registerBiometricRejectsDeviceThatDoesNotBelongToUser() {
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user()));
        when(deviceDetailsRepository.findByIdAndUser_Id("other-device", "user-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerBiometric(
                "user@example.com",
                biometricRequest("other-device", "BASE64URL_ENCODED_PUBLIC_KEY", "ES256")
        )).isInstanceOf(ResourceNotFound.class)
                .hasMessage("Device not found");
        verify(biometricCredentialRepository, never()).save(any());
        verify(otpRepository, never()).save(any());
    }

    @Test
    void registerBiometricRejectsUnsupportedAlgorithm() {
        User user = user();
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(deviceDetailsRepository.findByIdAndUser_Id("device-uuid", "user-id"))
                .thenReturn(Optional.of(existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"))));

        assertThatThrownBy(() -> service.registerBiometric(
                "user@example.com",
                biometricRequest("device-uuid", "BASE64URL_ENCODED_PUBLIC_KEY", "RS256")
        )).isInstanceOf(FunctionException.class)
                .hasMessage("Unsupported biometric algorithm. Use ES256.");
        verify(otpRepository, never()).save(any());
    }

    @Test
    void validatePublicKeyAcceptsKeyRegisteredForTheAuthenticatedUser() {
        User user = user();
        DeviceDetails device = existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"));
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.of(device));
        when(biometricCredentialRepository.findByDevice_Id("device-uuid"))
                .thenReturn(Optional.of(activeCredential(device)));

        ResponseEntity<ApiResponse> response = service.validatePublicKey(
                "user@example.com",
                new BiometricValidateRequest("OLD_KEY", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BiometricValidationResponse data = (BiometricValidationResponse) response.getBody().getData();
        assertThat(data.valid()).isTrue();
        assertThat(data.deviceId()).isEqualTo("device-uuid");
        assertThat(data.keyId()).isEqualTo("old-key");
        assertThat(data.algorithm()).isEqualTo("ES256");
        assertThat(data.status()).isEqualTo(ActivationStatus.ACTIVE);
    }

    @Test
    void validatePublicKeyRejectsKeyThatDoesNotBelongToTheUser() {
        User user = user();
        DeviceDetails device = existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"));
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.of(device));
        when(biometricCredentialRepository.findByDevice_Id("device-uuid"))
                .thenReturn(Optional.of(activeCredential(device)));

        ResponseEntity<ApiResponse> response = service.validatePublicKey(
                "user@example.com",
                new BiometricValidateRequest("SOME_OTHER_PUBLIC_KEY", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        BiometricValidationResponse data = (BiometricValidationResponse) response.getBody().getData();
        assertThat(data.valid()).isFalse();
    }

    @Test
    void validatePublicKeyRejectsRevokedCredential() {
        User user = user();
        DeviceDetails device = existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"));
        BiometricCredential credential = activeCredential(device);
        credential.setStatus(ActivationStatus.REVOKED);
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(deviceDetailsRepository.findByUser_Id("user-id")).thenReturn(Optional.of(device));
        when(biometricCredentialRepository.findByDevice_Id("device-uuid")).thenReturn(Optional.of(credential));

        ResponseEntity<ApiResponse> response = service.validatePublicKey(
                "user@example.com",
                new BiometricValidateRequest("OLD_KEY", null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        BiometricValidationResponse data = (BiometricValidationResponse) response.getBody().getData();
        assertThat(data.valid()).isFalse();
    }

    @Test
    void validatePublicKeyUsesDeviceIdWhenProvided() {
        User user = user();
        DeviceDetails device = existingDevice(user, Instant.parse("2026-09-01T08:00:00Z"));
        when(userRepository.findByEmailAddress("user@example.com")).thenReturn(Optional.of(user));
        when(deviceDetailsRepository.findByIdAndUser_Id("device-uuid", "user-id")).thenReturn(Optional.of(device));
        when(biometricCredentialRepository.findByDevice_Id("device-uuid"))
                .thenReturn(Optional.of(activeCredential(device)));

        ResponseEntity<ApiResponse> response = service.validatePublicKey(
                "user@example.com",
                new BiometricValidateRequest("OLD_KEY", "device-uuid")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BiometricValidationResponse data = (BiometricValidationResponse) response.getBody().getData();
        assertThat(data.valid()).isTrue();
        verify(deviceDetailsRepository, never()).findByUser_Id("user-id");
    }

    private User user() {
        User user = new User();
        user.setId("user-id");
        user.setEmailAddress("user@example.com");
        return user;
    }

    private DeviceDetailsRequest deviceRequest() {
        return new DeviceDetailsRequest("ios-install-abc-123", "David's iPhone", "IOS", "1.2.0", "otp-ref");
    }

    private UserOtp verifiedOtp() {
        UserOtp otp = new UserOtp();
        otp.setEmail("user@example.com");
        otp.setOtpReference("otp-ref");
        otp.markVerified();
        return otp;
    }

    private UserOtp pendingOtp() {
        UserOtp otp = new UserOtp();
        otp.setEmail("user@example.com");
        otp.setOtpReference("otp-ref");
        otp.setOtp("hashed-otp");
        return otp;
    }

    private BiometricRegisterRequest biometricRequest(String deviceId, String publicKey, String algorithm) {
        return new BiometricRegisterRequest(deviceId, publicKey, algorithm, "123456", "otp-ref");
    }

    private DeviceDetails existingDevice(User user, Instant firstSeen) {
        DeviceDetails device = new DeviceDetails();
        device.setId("device-uuid");
        device.setUser(user);
        device.setClientDeviceId("ios-install-abc-123");
        device.setDeviceName("David's iPhone");
        device.setPlatform(DevicePlatform.IOS);
        device.setAppVersion("1.2.0");
        device.setStatus(ActivationStatus.ACTIVE);
        device.setFirstSeenAt(firstSeen);
        device.setLastSeenAt(firstSeen);
        return device;
    }

    private BiometricCredential activeCredential(DeviceDetails device) {
        BiometricCredential credential = new BiometricCredential();
        credential.setKeyId("old-key");
        credential.setDevice(device);
        credential.setPublicKey("OLD_KEY");
        credential.setAlgorithm("ES256");
        credential.setStatus(ActivationStatus.ACTIVE);
        credential.setRegisteredAt(Instant.parse("2026-09-01T08:00:00Z"));
        return credential;
    }
}
