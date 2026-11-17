package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsCamelCaseAndSnakeCaseRefreshTokenFields() throws Exception {
        RefreshTokenRequest camelCase = objectMapper.readValue(
                "{\"refreshToken\":\"token-value\"}", RefreshTokenRequest.class);
        RefreshTokenRequest snakeCase = objectMapper.readValue(
                "{\"refresh_token\":\"token-value\"}", RefreshTokenRequest.class);

        assertThat(camelCase.refreshToken()).isEqualTo("token-value");
        assertThat(snakeCase.refreshToken()).isEqualTo("token-value");
    }
}