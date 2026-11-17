package com.myrtle.middleware.exceptions;

import lombok.Getter;

/**
 * The caller is authenticated but is not permitted to perform this action - as opposed to
 * {@link ResourceNotFound}, which would leak whether the resource exists.
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final Object data;

    public ForbiddenException(String message) {
        this(message, null);
    }

    public ForbiddenException(String message, Object data) {
        super(message);
        this.data = data;
    }
}
