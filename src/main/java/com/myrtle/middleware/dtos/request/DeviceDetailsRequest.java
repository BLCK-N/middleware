package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceDetailsRequest(
        @NotBlank(message = "Device ID is required")
        @Size(max = 255, message = "Device ID must be at most 255 characters")
        String deviceId,

        @Size(max = 255, message = "Device name must be at most 255 characters")
        String deviceName,

        String platform,

        @Size(max = 50, message = "App version must be at most 50 characters")
        String appVersion,

        /**
         * Required on {@code POST /device-details}. Login may omit it because the password
         * already proved the session.
         */
        String otpReference
) {
    public DeviceDetailsRequest(String deviceId, String deviceName, String platform, String appVersion) {
        this(deviceId, deviceName, platform, appVersion, null);
    }
}
