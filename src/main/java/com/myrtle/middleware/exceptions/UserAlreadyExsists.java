package com.myrtle.middleware.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class UserAlreadyExsists extends RuntimeException {
    public UserAlreadyExsists(String message) {
        super(message);
    }
}
