package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserLogin(
        @JsonAlias({"email", "emailAddress"}) String emailAddress,
        @JsonAlias({"email", "emailAddress"}) String email,
        String password,
        @JsonAlias({"bio_token", "bioToken"}) String bioToken,
        @JsonAlias({"device_id", "deviceId"}) String deviceId,
        @JsonAlias({"device_type", "deviceType"}) String deviceType,
        String ipAddress,
        DeviceDetailsRequest device
) {
    public UserLogin(String emailAddress, String password, String ipAddress) {
        this(emailAddress, emailAddress, password, null, null, null, ipAddress, null);
    }

    public UserLogin(String emailAddress, String password, String ipAddress, DeviceDetailsRequest device) {
        this(emailAddress, emailAddress, password, null, null, null, ipAddress, device);
    }

    public UserLogin(String emailAddress, String password, String ipAddress, DeviceDetailsRequest device, String email, String bioToken) {
        this(emailAddress, email, password, bioToken, null, null, ipAddress, device);
    }

    public String resolvedEmail() {
        return emailAddress != null && !emailAddress.isBlank() ? emailAddress : email;
    }
}