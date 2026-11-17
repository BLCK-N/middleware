package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.enums.ActivationStatus;
import com.myrtle.middleware.enums.DevicePlatform;
import com.myrtle.middleware.model.DeviceDetails;

import java.time.Instant;

public record DeviceDetailsResponse(
        String id,
        String userId,
        String clientDeviceId,
        String deviceName,
        DevicePlatform platform,
        String appVersion,
        ActivationStatus status,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant lastLoginAt
) {
    public static DeviceDetailsResponse from(DeviceDetails device) {
        return new DeviceDetailsResponse(
                device.getId(),
                device.getUser() != null ? device.getUser().getId() : null,
                device.getClientDeviceId(),
                device.getDeviceName(),
                device.getPlatform(),
                device.getAppVersion(),
                device.getStatus(),
                device.getFirstSeenAt(),
                device.getLastSeenAt(),
                device.getLastLoginAt()
        );
    }
}
