package com.myrtle.middleware.dtos.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class ApiResponse {

    private boolean success;
    private String message;
    private LocalDateTime timestamp = LocalDateTime.now();
    private Object data;

    public ApiResponse(boolean success, String message, Object data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse successResponse(String message, Object data) {
        return new ApiResponse(true, message, data);
    }

    public static ApiResponse badRequestResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse conflictResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse notFoundResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse unauthorizedResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse createdResponse(String message, Object data) {
        return new ApiResponse(true, message, data);
    }

    public static ApiResponse acceptedResponse(String message, Object data) {
        return new ApiResponse(true, message, data);
    }

    public static ApiResponse serverErrorResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse serviceUnavailableResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse forbiddenResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse badGatewayResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }

    public static ApiResponse payloadTooLargeResponse(String message, Object data) {
        return new ApiResponse(false, message, data);
    }
}
