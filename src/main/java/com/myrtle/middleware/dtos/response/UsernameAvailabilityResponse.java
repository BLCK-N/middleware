package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * MyWealth's username availability answer.
 *
 * <p>The endpoint returns a bare JSON boolean in the observed contract, but tolerates the common
 * wrapper shapes ({@code {"available": true}}, {@code {"isAvailable": true}}, {@code {"data": true}})
 * so a future response change surfaces as a normal false/true rather than a parse failure.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UsernameAvailabilityResponse {

    private Boolean available;

    @JsonAnySetter
    void anyField(String name, Object value) {
        if (available != null || !(value instanceof Boolean flag)) {
            return;
        }
        if ("available".equalsIgnoreCase(name)
                || "isAvailable".equalsIgnoreCase(name)
                || "data".equalsIgnoreCase(name)
                || "result".equalsIgnoreCase(name)) {
            this.available = flag;
        }
    }

    public boolean isAvailable() {
        return Boolean.TRUE.equals(available);
    }

    /**
     * True when the upstream answer could not be interpreted, so the caller can fail safe.
     */
    public boolean isIndeterminate() {
        return available == null;
    }

    public UsernameAvailabilityResponse(boolean available) {
        this.available = available;
    }

    /**
     * Leaves {@code available} unset (indeterminate), not {@code false}. A no-arg instance is used
     * as the sentinel for "MyWealth was unreachable or the reply was unrecognised" - if this defaulted
     * to false, that would be indistinguishable from a confirmed "username taken", and callers would
     * report a transient MyWealth outage as a permanent 409 instead of a retryable 503. It would also
     * block {@link #anyField} below, whose guard only assigns when {@code available} is still null,
     * from ever recognising the isAvailable/data/result wrapper shapes.
     */
    public UsernameAvailabilityResponse() {
    }
}
