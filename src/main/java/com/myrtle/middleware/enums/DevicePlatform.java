package com.myrtle.middleware.enums;

import java.util.Locale;

public enum DevicePlatform {
    IOS,
    ANDROID,
    WEB;

    public static DevicePlatform from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DevicePlatform.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
