package com.myrtle.middleware.custom;

import com.myrtle.middleware.services.third_party.ThirdPartyLoggingRequestService;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Service
public class GenericRequestService {

    private final RestTemplate restTemplate;
    private final ThirdPartyLoggingRequestService loggingRequestService;

    public GenericRequestService(
            RestTemplate restTemplate,
            @Lazy ThirdPartyLoggingRequestService loggingRequestService) {
        this.restTemplate = restTemplate;
        this.loggingRequestService = loggingRequestService;
    }

    public <T, R> ResponseEntity<R> sendRequest(GenericRequest<T> request, Class<R> responseType) {
        Instant requestTimestamp = Instant.now();
        HttpHeaders httpHeaders = new HttpHeaders();
        if (request.getHeaders() != null) {
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                httpHeaders.add(entry.getKey(), entry.getValue());
            }
        }

        HttpEntity<T> entity = new HttpEntity<>(request.getBody(), httpHeaders);
        HttpMethod method = HttpMethod.valueOf(request.getMethod().name());

        try {
            ResponseEntity<R> response = restTemplate.exchange(
                    request.getUrl(),
                    method,
                    entity,
                    responseType
            );
            Instant responseTimestamp = Instant.now();
            loggingRequestService.logRequest(request, response, null, requestTimestamp, responseTimestamp);
            return response;
        } catch (Exception e) {
            Instant responseTimestamp = Instant.now();
            loggingRequestService.logRequest(request, null, e, requestTimestamp, responseTimestamp);
            throw e;
        }
    }
}
