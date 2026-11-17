package com.myrtle.middleware.dtos.response;

import lombok.Data;

import java.util.List;

@Data
public class ExternalLoginResult {

    private boolean success;
    private String errorCode;
    private ExternalErrorResponse message;
    private List<ExternalValidationErrorResponse.FieldError> fieldErrors;
    private ClientLoginResponse data;

    public static ExternalLoginResult success(ClientLoginResponse data) {
        ExternalLoginResult r = new ExternalLoginResult();
        r.success = true;
        r.data = data;
        return r;
    }

    public static ExternalLoginResult validationFailure(
            ExternalErrorResponse message,
            List<ExternalValidationErrorResponse.FieldError> fieldErrors) {

        ExternalLoginResult r = new ExternalLoginResult();
        r.success = false;
        r.errorCode = "VALIDATION_FAILED";
        r.message = message;
        r.fieldErrors = fieldErrors;
        return r;
    }

    public static ExternalLoginResult failure(String code, ExternalErrorResponse message) {
        ExternalLoginResult r = new ExternalLoginResult();
        r.success = false;
        r.errorCode = code;
        r.message = message;
        return r;
    }

    @Data
    public class ExternalErrorResponse {

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

    @Data
    public class ExternalErrorResponse2{
        private String timestamp;
        private String summary;
        private String code;
        private ExternalErrorResponse details;
        private List<ExternalErrorResponse.FieldError> fields;

        @Data
        public static class FieldError {
            private String property;
            private String message;
        }
    }
}


