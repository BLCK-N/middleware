package com.myrtle.middleware.exceptions;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.utils.ResponseUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final Set<String> CORPORATE_CLIENT_UNIQUE_CONSTRAINTS = Set.of(
            "uk_corporate_clients_email",
            "uk_corporate_clients_bvn",
            "uk_corporate_clients_mobile_no",
            "uk_corporate_clients_primary_contact_email",
            "uk_corporate_clients_primary_contact_username",
            "uk_corporate_clients_primary_contact_mobile_no",
            "uk_corporate_clients_ref_code"
    );

    private final ResponseUtil responseUtil;

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse> handleMaxSizeException(MaxUploadSizeExceededException ex) {
        return responseUtil.payloadTooLarge("File size exceeds the maximum allowed limit (20MB)", null);
    }

    @ExceptionHandler(FunctionException.class)
    public ResponseEntity<ApiResponse> handleFunctionException(FunctionException e) {
        log.warn("FunctionException | message={} | data={}", e.getMessage(), e.getData());
        return responseUtil.fromFunctionException(e);
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse> handleInvalidRefreshToken(InvalidRefreshTokenException e) {
        log.warn("Refresh token rejected | message={}", e.getMessage());
        return responseUtil.unauthorized(e.getMessage(), null);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ApiResponse> handleUpstreamServiceException(UpstreamServiceException e) {
        log.error("UpstreamServiceException -> 502 BAD_GATEWAY | provider={} | upstreamStatus={} | message={} | data={}",
                e.getProvider(), e.getUpstreamStatus(), e.getMessage(), e.getData(), e);
        return responseUtil.badGateway(e.getMessage(), e.getData());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse> handleDuplicateResource(DuplicateResourceException e) {
        return responseUtil.conflict(e.getMessage(), Map.of("field", e.getField()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (isCorporateClientDuplicateConstraint(ex)) {
            log.warn("Duplicate corporate account database value rejected", ex);
            return responseUtil.conflict(
                    "Duplicate corporate account value already exists",
                    Map.of("field", "unknown")
            );
        }

        log.error("Database integrity violation rejected request", ex);
        return responseUtil.serverError(
                "A database constraint rejected the request. Please try again or contact support for assistance.",
                null
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", resolveMessage(error)
                ))
                .toList();

        return responseUtil.badRequest("Validation failed", Map.of("errors", errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolations(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violatedField(violation),
                        "message", violation.getMessage()
                ))
                .toList();

        return responseUtil.badRequest("Validation failed", Map.of("errors", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse> handleUnreadableRequest(HttpMessageNotReadableException ex) {
        return responseUtil.badRequest("Validation failed", Map.of(
                "errors",
                List.of(Map.of(
                        "field", "request",
                        "message", "Request body contains invalid or unreadable values"
                ))
        ));
    }

    /**
     * Method-level violations report the whole path (e.g. "getOpenJobListings.size"), but the
     * client only knows the parameter name.
     */
    private String violatedField(ConstraintViolation<?> violation) {
        String field = null;
        for (Path.Node node : violation.getPropertyPath()) {
            field = node.getName();
        }
        return field == null || field.isBlank() ? "request" : field;
    }

    private String resolveMessage(DefaultMessageSourceResolvable error) {
        String message = error.getDefaultMessage();
        return message == null || message.isBlank() ? "Invalid value" : message;
    }

    private boolean isCorporateClientDuplicateConstraint(DataIntegrityViolationException ex) {
        String message = databaseErrorMessage(ex).toLowerCase(Locale.ROOT);
        return CORPORATE_CLIENT_UNIQUE_CONSTRAINTS.stream().anyMatch(message::contains);
    }

    private String databaseErrorMessage(DataIntegrityViolationException ex) {
        Throwable mostSpecificCause = ex.getMostSpecificCause();
        if (mostSpecificCause != null && mostSpecificCause.getMessage() != null) {
            return mostSpecificCause.getMessage();
        }
        return ex.getMessage() == null ? "" : ex.getMessage();
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<ApiResponse> handleCompletionExceptions(CompletionException e) {
        log.warn("CompletionException -> 400 BAD_REQUEST | message={} | cause={}",
                e.getMessage(), e.getCause() != null ? e.getCause().toString() : "none", e);
        return responseUtil.badRequest(e.getMessage(), null);
    }

    @ExceptionHandler(ResourceNotFound.class)
    public ResponseEntity<ApiResponse> handleResourceNotFound(ResourceNotFound e) {
        return responseUtil.notFound(e.getMessage(), null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse> handleForbidden(ForbiddenException e) {
        log.warn("ForbiddenException | message={}", e.getMessage());
        return responseUtil.forbidden(e.getMessage(), e.getData());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleOtherExceptions(Exception e) {
        log.error("Unhandled exception -> 500 INTERNAL_SERVER_ERROR | type={} | message={}",
                e.getClass().getName(), e.getMessage(), e);
        return responseUtil.serverError("something went wrong. please try again.", null);
    }
}
