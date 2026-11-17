package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.DevicePlatform;
import com.myrtle.middleware.model.UserBiometricDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserBiometricDeviceRepository extends JpaRepository<UserBiometricDevice, String> {

    Optional<UserBiometricDevice> findByUser_IdAndDeviceIdAndDeviceType(String userId, String deviceId, DevicePlatform deviceType);

    Optional<UserBiometricDevice> findByBioTokenHash(String bioTokenHash);

    Optional<UserBiometricDevice> findByUser_IdAndDeviceId(String userId, String deviceId);
}
