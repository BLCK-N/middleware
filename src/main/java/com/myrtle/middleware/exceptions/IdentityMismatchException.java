package com.myrtle.middleware.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class IdentityMismatchException extends RuntimeException {
    public IdentityMismatchException(String message) {
        super(message);
    }
}
