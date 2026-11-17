package com.myrtle.middleware.exceptions;

public class DuplicateResourceException extends RuntimeException {

    private final String field;

    public DuplicateResourceException(String message, String field) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
