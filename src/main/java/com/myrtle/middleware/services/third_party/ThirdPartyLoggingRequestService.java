package com.myrtle.middleware.services.third_party;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.custom.GenericRequest;
import com.myrtle.middleware.custom.GenericRequestService;
import com.myrtle.middleware.model.ThirdPartyActivityLog;
import com.myrtle.middleware.repository.ThirdPartyLogsRepo;
import com.myrtle.middleware.utils.RequestAuditContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.Instant;

/**
 * Wraps {@link GenericRequestService} and records every outbound request/response
 * into {@link ThirdPartyActivityLog} for monitoring third-party API usage.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThirdPartyLoggingRequestService {

    private static final int MAX_PAYLOAD_LENGTH = 15_000;

    private final GenericRequestService genericRequestService;
    private final ThirdPartyLogsRepo thirdPartyLogsRepo;
    private final ObjectMapper objectMapper;

    @Value("${mywealth.api.host:}")
    private String mywealthHost;
    @Value("${qoreid.api.host:}")
    private String qoreIdHost;
    @Value("${gemini.api.host:}")
    private String geminiHost;
    @Value("${flutterwave.api.base-url:}")
    private String flutterwaveBaseUrl;

    /**
     * Sends the request via GenericRequestService. Logging is performed inside GenericRequestService
     * via {@link #logRequest}, so every 3rd party request is recorded in one place.
     */
    public <T, R> ResponseEntity<R> sendRequest(GenericRequest<T> request, Class<R> responseType) {
        return genericRequestService.sendRequest(request, responseType);
    }

    /**
     * Logs an outbound request/response from GenericRequestService.
     * Called by GenericRequestService so every 3rd party API request is recorded.
     *
     * @param request           the request that was sent
     * @param response          the response (null if the call failed)
     * @param failure           the exception (null if the call succeeded)
     * @param requestTimestamp  when the request was sent
     * @param responseTimestamp when the response was received or the failure occurred
     */
    public void logRequest(
            GenericRequest<?> request,
            ResponseEntity<?> response,
            Exception failure,
            Instant requestTimestamp,
            Instant responseTimestamp
    ) {
        if (request == null) return;
        String url = request.getUrl();
        String provider = resolveProvider(url);
        String httpMethod = request.getMethod() != null ? request.getMethod().name() : null;
        String requestHeaders = toJsonSafe(request.getHeaders());
        String requestPayload = toJsonSafe(request.getBody());

        Integer httpStatusCode = null;
        String responsePayload = null;
        if (response != null) {
            httpStatusCode = response.getStatusCode() != null ? response.getStatusCode().value() : null;
            responsePayload = toJsonSafe(response.getBody());
        } else if (failure != null) {
            httpStatusCode = statusFromException(failure);
            try {
                if (failure instanceof HttpClientErrorException ex) {
                    responsePayload = ex.getResponseBodyAsString();
                } else if (failure instanceof HttpServerErrorException ex) {
                    responsePayload = ex.getResponseBodyAsString();
                } else {
                    responsePayload = failure.getMessage();
                }
            } catch (Exception ignored) {
                responsePayload = failure.getMessage();
            }
            responsePayload = truncate(responsePayload);
        }

        saveLog(
                provider,
                url,
                httpMethod,
                requestHeaders,
                requestPayload,
                httpStatusCode,
                responsePayload,
                requestTimestamp,
                responseTimestamp
        );
    }

    /**
     * Records an outbound call that was made outside GenericRequestService (e.g. AsyncHttpClient).
     * Use this from ExternalAPIs for convertLeadToClient and postTransaction.
     */
    public void saveLog(
            String providerName,
            String apiEndpoint,
            String httpMethod,
            String requestHeaders,
            String requestPayload,
            Integer httpStatusCode,
            String responsePayload,
            Instant requestTimestamp,
            Instant responseTimestamp
    ) {
        if (httpStatusCode != null && (httpStatusCode < 200 || httpStatusCode >= 300)) {
            log.error("External API non-2xx | provider={} | method={} | url={} | status={} | response={}",
                    providerName != null ? providerName : "External",
                    httpMethod,
                    apiEndpoint,
                    httpStatusCode,
                    truncate(responsePayload, 4000));
        }

        try {
            try {
                requestPayload = objectMapper.writeValueAsString(requestPayload);
                responsePayload = objectMapper.writeValueAsString(responsePayload);
            } catch (Exception e) {
                log.info("error converting payload to JSON ");
            }
            ThirdPartyActivityLog logEntry = new ThirdPartyActivityLog();
            logEntry.setSessionId(RequestAuditContext.getSessionId());
            logEntry.setRequestTime(requestTimestamp);
            logEntry.setRequestTimestamp(requestTimestamp);
            logEntry.setResponseTimestamp(responseTimestamp);
            logEntry.setCreatedAt(Instant.now());
            logEntry.setApiEndpoint(truncate(apiEndpoint, 2048));
            logEntry.setThirdPartyProviderName(providerName != null ? truncate(providerName, 128) : "External");
            logEntry.setHttpMethod(httpMethod);
            logEntry.setRequestHeaders(truncate(sanitizeHeaders(requestHeaders), 2048));
            logEntry.setRequestPayload(requestPayload);
            logEntry.setResponseHeaders(null);
            logEntry.setResponsePayload(responsePayload);
            logEntry.setHttpStatusCode(httpStatusCode);
            thirdPartyLogsRepo.save(logEntry);
        } catch (Exception e) {
            log.warn("Failed to persist third-party activity log for {} {}", httpMethod, apiEndpoint);
        }
    }

    private String resolveProvider(String url) {
        if (url == null) return "External";
        if (qoreIdHost != null && !qoreIdHost.isBlank() && url.startsWith(qoreIdHost)) return "QoreID";
        if (geminiHost != null && !geminiHost.isBlank() && url.startsWith(geminiHost)) return "Gemini";
        if (mywealthHost != null && !mywealthHost.isBlank() && url.startsWith(mywealthHost)) return "MyWealth";
        if (flutterwaveBaseUrl != null && !flutterwaveBaseUrl.isBlank() && url.startsWith(flutterwaveBaseUrl))
            return "Flutterwave";
        return "External";
    }

    private static Integer statusFromException(Exception e) {
        if (e instanceof org.springframework.web.client.HttpClientErrorException ex) {
            return ex.getStatusCode().value();
        }
        if (e instanceof org.springframework.web.client.HttpServerErrorException ex) {
            return ex.getStatusCode().value();
        }
        return null;
    }

    private String toJsonSafe(Object obj) {
        if (obj == null) return null;
        try {
            return maskSensitivePayload(objectMapper.writeValueAsString(obj));
        } catch (Exception e) {
            return maskSensitivePayload(String.valueOf(obj));
        }
    }

    private static String sanitizeHeaders(String headersJson) {
        if (headersJson == null || headersJson.isEmpty()) return headersJson;
        try {
            return headersJson
                    .replaceAll("Bearer [^\"]+", "Bearer ***")
                    .replaceAll("(?i)authorization[\"\\s:]+[^\"]*", "Authorization: ***");
        } catch (Exception e) {
            return headersJson;
        }
    }

    private static String maskSensitivePayload(String payload) {
        if (payload == null || payload.isEmpty()) return payload;
        try {
            return payload
                    .replaceAll("\"password\"\\s*:\\s*\".*?\"", "\"password\":\"***\"")
                    .replaceAll("\"pin\"\\s*:\\s*\".*?\"", "\"pin\":\"***\"");
        } catch (Exception e) {
            return payload;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...[truncated]";
    }

    private static String truncate(String s) {
        return truncate(s, MAX_PAYLOAD_LENGTH);
    }
}
