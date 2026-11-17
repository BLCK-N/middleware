package com.myrtle.middleware.utils;

import com.myrtle.middleware.dtos.request.FunctionResponse;
import com.myrtle.middleware.dtos.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@Slf4j
@Service
public class ResponseUtil {


    private ResponseEntity<ApiResponse> respond(HttpStatus status, ApiResponse body, String message, Object data) {
        if (status.is5xxServerError()) {
            log.error("Client response | status={} {} | message={} | data={}",
                    status.value(), status.getReasonPhrase(), message, data);
        } else if (!status.is2xxSuccessful()) {
            log.warn("Client response | status={} {} | message={} | data={}",
                    status.value(), status.getReasonPhrase(), message, data);
        }
        return new ResponseEntity<>(body, status);
    }

    public ResponseEntity<ApiResponse> success(String message, Object data) {
        return new ResponseEntity<>(ApiResponse.successResponse(message, data), OK);
    }

    public ResponseEntity<ApiResponse> badRequest(String message, Object data) {
        return respond(BAD_REQUEST, ApiResponse.badRequestResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> conflict(String message, Object data) {
        return respond(CONFLICT, ApiResponse.conflictResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> notFound(String message, Object data) {
        return respond(NOT_FOUND, ApiResponse.notFoundResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> unauthorized(String message, Object data) {
        return respond(UNAUTHORIZED, ApiResponse.unauthorizedResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> created(String message, Object data) {
        return new ResponseEntity<>(ApiResponse.createdResponse(message, data), CREATED);
    }

    public ResponseEntity<ApiResponse> accepted(String message, Object data) {
        return new ResponseEntity<>(ApiResponse.acceptedResponse(message, data), ACCEPTED);
    }

    public ResponseEntity<ApiResponse> serverError(String message, Object data) {
        return respond(INTERNAL_SERVER_ERROR, ApiResponse.serverErrorResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> serviceUnavailable(String message, Object data) {
        return respond(SERVICE_UNAVAILABLE, ApiResponse.serviceUnavailableResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> forbidden(String message, Object data) {
        return respond(FORBIDDEN, ApiResponse.forbiddenResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> badGateway(String message, Object data) {
        return respond(BAD_GATEWAY, ApiResponse.badGatewayResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> payloadTooLarge(String message, Object data) {
        return respond(PAYLOAD_TOO_LARGE, ApiResponse.payloadTooLargeResponse(message, data), message, data);
    }

    public ResponseEntity<ApiResponse> fromFunctionResponse(FunctionResponse fr) {
        if (fr == null) return serverError("An error occurred", null);

        return switch (fr.status()) {
            case OK -> success(fr.message(), fr.data());
            case CREATED -> created(fr.message(), fr.data());
            case ACCEPTED -> accepted(fr.message(), fr.data());
            case BAD_REQUEST -> badRequest(fr.message(), fr.data());
            case NOT_FOUND -> notFound(fr.message(), fr.data());
            case UNAUTHORIZED -> unauthorized(fr.message(), fr.data());
            case CONFLICT -> conflict(fr.message(), fr.data());
            case SERVICE_UNAVAILABLE -> serviceUnavailable(fr.message(), fr.data());
            default -> serverError(fr.message(), fr.data());
        };
    }

    public ResponseEntity<ApiResponse> fromFunctionException(com.myrtle.middleware.exceptions.FunctionException e) {
        Integer upstreamStatus = extractUpstreamStatus(e.getData());
        if (upstreamStatus != null) {
            return fromUpstreamClientError(upstreamStatus, e.getMessage(), e.getData());
        }
        return badRequest(e.getMessage(), e.getData());
    }

    public ResponseEntity<ApiResponse> fromUpstreamClientError(int upstreamStatus, String message, Object data) {
        HttpStatus status = mapUpstreamStatus(upstreamStatus);
        ApiResponse body = switch (status) {
            case CONFLICT -> ApiResponse.conflictResponse(message, data);
            case NOT_FOUND -> ApiResponse.notFoundResponse(message, data);
            case UNAUTHORIZED -> ApiResponse.unauthorizedResponse(message, data);
            case FORBIDDEN -> ApiResponse.forbiddenResponse(message, data);
            case BAD_GATEWAY -> ApiResponse.badGatewayResponse(message, data);
            default -> ApiResponse.badRequestResponse(message, data);
        };
        return respond(status, body, message, data);
    }

    private HttpStatus mapUpstreamStatus(int upstreamStatus) {
        return switch (upstreamStatus) {
            case 409 -> CONFLICT;
            case 404 -> NOT_FOUND;
            case 401 -> UNAUTHORIZED;
            case 403 -> FORBIDDEN;
            case 502, 503, 504 -> BAD_GATEWAY;
            default -> upstreamStatus >= 400 && upstreamStatus < 500 ? BAD_REQUEST : BAD_GATEWAY;
        };
    }

    private Integer extractUpstreamStatus(Object data) {
        if (!(data instanceof Map<?, ?> map)) {
            return null;
        }
        Object status = map.get("upstreamStatus");
        if (status instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
