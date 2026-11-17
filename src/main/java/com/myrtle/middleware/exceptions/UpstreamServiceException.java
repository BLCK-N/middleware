package com.myrtle.middleware.exceptions;

import lombok.Getter;

@Getter
public class UpstreamServiceException extends RuntimeException {
    private final String provider;
    private final Integer upstreamStatus;
    private final transient Object data;

    public UpstreamServiceException(String provider, Integer upstreamStatus, String message, Object data) {
        super(message);
        this.provider = provider;
        this.upstreamStatus = upstreamStatus;
        this.data = data;
    }

    public UpstreamServiceException(String provider, String message) {
        this(provider, null, message, null);
    }

    public UpstreamServiceException(String provider, String message, Object data) {
        this(provider, null, message, data);
    }
}
