package com.myrtle.middleware.dtos.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshTokenRequest(@JsonAlias({"refresh_token", "refresh-token"}) String refreshToken) {
}
