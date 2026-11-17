package com.myrtle.middleware.utils;

import java.util.UUID;

/**
 * Holds request-scoped audit data (e.g. sessionId) for the current thread.
 * Set by the request audit filter so that TxnAuditLogs and ThirdPartyActivityLog
 * for the same request share the same sessionId.
 */
public final class RequestAuditContext {

    private static final ThreadLocal<String> SESSION_ID = new ThreadLocal<>();

    /**
     * Sets the sessionId for the current request. Call at the start of request processing
     * (e.g. in the audit filter). Use {@link #clear()} in a finally block when the request ends.
     */
    public static void setSessionId(String sessionId) {
        SESSION_ID.set(sessionId);
    }

    /**
     * Gets the sessionId for the current request, or null if not set (e.g. request did not go through the audit filter).
     */
    public static String getSessionId() {
        return SESSION_ID.get();
    }

    /**
     * Clears the thread-local. Must be called when the request ends to avoid leaks.
     */
    public static void clear() {
        SESSION_ID.remove();
    }

    /**
     * Generates a new session ID (UUID) and sets it for the current request.
     * Returns the generated value.
     */
    public static String initSessionId() {
        String sessionId = String.valueOf(UUID.randomUUID());
        setSessionId(sessionId);
        return sessionId;
    }

    private RequestAuditContext() {
    }
}
