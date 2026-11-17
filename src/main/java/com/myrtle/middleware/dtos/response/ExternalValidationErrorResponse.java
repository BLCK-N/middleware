package com.myrtle.middleware.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class ExternalValidationErrorResponse {

    private String timestamp;
    private String summary;
    private String code;
    private String details;
    private List<FieldError> fields;

    @Data
    public static class FieldError {
        private String property;
        private String message;
    }

}

