package com.myrtle.middleware.dtos.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The MyWealth contract shows a bare boolean. These pin the parsing so a wrapper-shaped reply is
 * still understood, and - critically - an unrecognised reply reads as indeterminate rather than
 * available.
 */
class UsernameAvailabilityResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private UsernameAvailabilityResponse parse(String json) throws Exception {
        return mapper.readValue(json, UsernameAvailabilityResponse.class);
    }

    @Test
    void readsTheWrappedAvailableField() throws Exception {
        UsernameAvailabilityResponse response = parse("{\"available\": true}");

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.isIndeterminate()).isFalse();
    }

    @Test
    void readsAWrappedFalse() throws Exception {
        UsernameAvailabilityResponse response = parse("{\"available\": false}");

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.isIndeterminate()).isFalse();
    }

    @Test
    void readsTheIsAvailableVariant() throws Exception {
        assertThat(parse("{\"isAvailable\": true}").isAvailable()).isTrue();
    }

    @Test
    void readsTheDataVariant() throws Exception {
        assertThat(parse("{\"data\": false}").isAvailable()).isFalse();
    }

    @Test
    void anUnrecognisedShapeIsIndeterminateNotAvailable() throws Exception {
        UsernameAvailabilityResponse response = parse("{\"status\": \"OK\", \"count\": 3}");

        assertThat(response.isIndeterminate()).isTrue();
        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    void aDefaultInstanceIsIndeterminate() {
        UsernameAvailabilityResponse response = new UsernameAvailabilityResponse();

        assertThat(response.isIndeterminate()).isTrue();
        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    void unknownFieldsDoNotBreakParsing() throws Exception {
        UsernameAvailabilityResponse response = parse("{\"available\": true, \"somethingNew\": \"x\"}");

        assertThat(response.isAvailable()).isTrue();
    }
}
