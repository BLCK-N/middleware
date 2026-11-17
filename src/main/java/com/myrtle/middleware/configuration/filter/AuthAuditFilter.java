package com.myrtle.middleware.configuration.filter;

import com.myrtle.middleware.model.TxnAuditLogs;
import com.myrtle.middleware.repository.TxnLogRepo;
import com.myrtle.middleware.utils.RequestAuditContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Populates {@link TxnAuditLogs} for every request (all endpoints).
 * Runs after JWT filter so SecurityContext is available for authenticated requests.
 * Override {@link #shouldNotFilter(HttpServletRequest)} to exclude paths (e.g. /health, /actuator/**).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthAuditFilter extends OncePerRequestFilter {

    private static final int MAX_PAYLOAD_LOG_LENGTH = 10_000;

    /** Paths to skip for audit (e.g. health checks that would flood the table). */
    private static final String[] SKIP_PATHS = { "/health", "/actuator" };

    private final TxnLogRepo txnLogRepo;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        for (String skip : SKIP_PATHS) {
            if (uri.startsWith(skip)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        RequestAuditContext.initSessionId();
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            try {
                logRequest(requestWrapper, responseWrapper);
            } catch (Exception e) {
                log.warn("Failed to persist audit log for {} {}", request.getMethod(), request.getRequestURI(), e);
            }
            responseWrapper.copyBodyToResponse();
            RequestAuditContext.clear();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request,
                            ContentCachingResponseWrapper response) {
        TxnAuditLogs logEntry = new TxnAuditLogs();

        logEntry.setSessionId(RequestAuditContext.getSessionId());
        logEntry.setEventTimestamp(java.time.Instant.now());
        logEntry.setCreatedAt(java.time.Instant.now());
        logEntry.setHttpMethod(request.getMethod());
        logEntry.setEndpoint(request.getRequestURI());
        logEntry.setClientIp(resolveClientIp(request));
        logEntry.setHttpStatusCode(response.getStatus());
        logEntry.setIsSuccessful(response.getStatus() >= 200 && response.getStatus() < 400);
        logEntry.setOperation(request.getMethod() + " " + request.getRequestURI());
        logEntry.setAction_description("Request: " + request.getMethod() + " " + request.getRequestURI());
        logEntry.setChannel(resolveChannel(request));

        resolveUserEmail().ifPresent(logEntry::setUserEmail);
        resolveUserId().ifPresent(logEntry::setUserId);

        String requestPayload = getTruncatedString(request.getContentAsByteArray());
        if (!requestPayload.isEmpty()) {
            logEntry.setRequestPayload(Base64.getEncoder().encodeToString(requestPayload.getBytes(StandardCharsets.UTF_8)));
        }

        String responsePayload = getTruncatedString(response.getContentAsByteArray());
        if (!responsePayload.isEmpty()) {
            logEntry.setResponsePayload(responsePayload);
        }

        txnLogRepo.save(logEntry);
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String resolveChannel(HttpServletRequest request) {
        String channel = request.getHeader("X-Client-Channel");
        if (channel != null && !channel.isBlank()) {
            return channel.toUpperCase();
        }
        String ua = request.getHeader("User-Agent");
        if (ua != null && (ua.contains("Android") || ua.contains("iOS") || ua.contains("Mobile"))) {
            return "MOBILE";
        }
        return "WEB";
    }

    private Optional<String> resolveUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) {
            return Optional.ofNullable(ud.getUsername());
        }
        if (principal instanceof String s) {
            return Optional.of(s);
        }
        return Optional.empty();
    }

    private Optional<String> resolveUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getPrincipal() == null) {
            return Optional.empty();
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof com.myrtle.middleware.model.User user) {
            return Optional.ofNullable(user.getId());
        }
        return Optional.empty();
    }

    private static String getTruncatedString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > MAX_PAYLOAD_LOG_LENGTH) {
            return value.substring(0, MAX_PAYLOAD_LOG_LENGTH) + "...[truncated]";
        }
        return value;
    }
}
