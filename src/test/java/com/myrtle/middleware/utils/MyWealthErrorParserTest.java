package com.myrtle.middleware.utils;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MyWealthErrorParserTest {

    @Test
    void parseDuplicateKeyErrorReturnsConflictFriendlyPayload() {
        String responseBody = """
                {
                  "timestamp": "2026-07-24T07:35:32.415206245",
                  "summary": "Duplicate Key",
                  "code": "409",
                  "details": "Duplicate username {chidi.okeke+23@example.com} found in contact records",
                  "fields": []
                }
                """;

        MyWealthErrorParser.ParsedError parsed = MyWealthErrorParser.parseClientError(409, responseBody);
        Map<String, Object> data = MyWealthErrorParser.toApiData(parsed);

        assertThat(parsed.userMessage())
                .isEqualTo("Duplicate username chidi.okeke+23@example.com found in contact records");
        assertThat(data.get("provider")).isEqualTo("MyWealth");
        assertThat(data.get("upstreamStatus")).isEqualTo(409);
        assertThat(data.get("summary")).isEqualTo("Duplicate Key");
        assertThat(data.get("code")).isEqualTo("409");
        assertThat(data.get("details"))
                .isEqualTo("Duplicate username {chidi.okeke+23@example.com} found in contact records");
    }

    @Test
    void parseValidationErrorExtractsFieldErrorsFromDetails() {
        String responseBody = """
                {
                  "timestamp": "2026-07-24T07:26:13.711571553",
                  "summary": "Validation Failed",
                  "code": "400",
                  "details": "contact[].birthDate: {must not be null}, contact[].address.country: {size must be between 2 and 3}",
                  "fields": []
                }
                """;

        MyWealthErrorParser.ParsedError parsed = MyWealthErrorParser.parseClientError(400, responseBody);

        assertThat(parsed.userMessage()).isEqualTo("Validation failed: contact[].birthDate must not be null");
        assertThat(parsed.error().getFields()).hasSize(2);
        assertThat(parsed.error().getFields().get(0).getProperty()).isEqualTo("contact[].birthDate");
        assertThat(parsed.error().getFields().get(0).getMessage()).isEqualTo("must not be null");
    }
}
