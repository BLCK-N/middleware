package com.myrtle.middleware.dtos.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QoreIdTokenResponse {
    private String accessToken;
    private int expiresIn;
}
