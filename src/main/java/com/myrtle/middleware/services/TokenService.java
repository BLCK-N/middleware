package com.myrtle.middleware.services;

import com.myrtle.middleware.exceptions.UpstreamServiceException;
import com.myrtle.middleware.model.MyWealthAccessToken;
import com.myrtle.middleware.model.TokenRequest;
import com.myrtle.middleware.model.TokenResponse;
import com.myrtle.middleware.repository.MyWealthAccessTokenRepository;
import com.myrtle.middleware.services.third_party.ThirdPartyLoggingRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private static final int EXPIRY_BUFFER_SECONDS = 10;

    private final RestTemplate restTemplate = new RestTemplate();
    private final MyWealthAccessTokenRepository tokenRepository;
    private final ThirdPartyLoggingRequestService loggingRequestService;

    @Value("${mywealth.api.host}")
    private String mywealthApiHost;

    @Value("${mywealth.daemon.username}")
    private String daemonUsername;

    @Value("${mywealth.daemon.password}")
    private String daemonPassword;

    @Value("${mywealth.tenant.id}")
    private String tenantId;

    private volatile MyWealthAccessToken cachedToken;

    public synchronized String getAccessToken() {
        if (cachedToken != null && !isExpiring(cachedToken.getExpiresAt())) {
            return cachedToken.getAccessToken();
        }

        MyWealthAccessToken persisted = tokenRepository.findById(MyWealthAccessToken.SINGLETON_ID).orElse(null);
        if (persisted != null && !isExpiring(persisted.getExpiresAt())) {
            cachedToken = persisted;
            return persisted.getAccessToken();
        }

        MyWealthAccessToken refreshed = fetchAndPersistNewToken();
        cachedToken = refreshed;
        return refreshed.getAccessToken();
    }

    private boolean isExpiring(Instant expiresAt) {
        return expiresAt == null || !Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_BUFFER_SECONDS));
    }

    private MyWealthAccessToken fetchAndPersistNewToken() {
        String url = mywealthApiHost + "/administration/api/v1/users/token/daemon";
        TokenRequest tokenRequest = new TokenRequest(daemonUsername, daemonPassword, tenantId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TokenRequest> request = new HttpEntity<>(tokenRequest, headers);

        try {
            ResponseEntity<TokenResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, TokenResponse.class);
            loggingRequestService.saveLog("MyWealth", url, "POST", headers.toString(), request.toString(), null, String.valueOf(response), Instant.now(), null);

            TokenResponse body = response.getBody();
            if (body == null || body.getAccessToken() == null) {
                throw new UpstreamServiceException("MyWealth", "Something went wrong. Please try again later.");
            }

            MyWealthAccessToken entity = tokenRepository.findById(MyWealthAccessToken.SINGLETON_ID)
                    .orElseGet(MyWealthAccessToken::new);
            entity.setId(MyWealthAccessToken.SINGLETON_ID);
            entity.setTokenType(body.getTokenType());
            entity.setAccessToken(body.getAccessToken());
            entity.setRefreshToken(body.getRefreshToken());
            entity.setScope(body.getScope());
            entity.setExpiresIn(body.getExpiresIn());
            entity.setRefreshExpiresIn(body.getRefreshExpiresIn());
            entity.setExpiresAt(Instant.now().plusSeconds(body.getExpiresIn()));

            log.info("Refreshed MyWealth daemon access token | tenantId={} | expiresIn={}s", tenantId, body.getExpiresIn());
            return tokenRepository.save(entity);
        } catch (UpstreamServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to acquire MyWealth daemon access token | url={} | tenantId={} | reason={}", url, tenantId, e.getMessage(), e);
            loggingRequestService.saveLog("MyWealth", url, "POST", headers.toString(), request.toString(),
                    null, e.getMessage(), Instant.now(), null);
            throw new RuntimeException("Something went wrong. Please try again.");
        }
    }
}
