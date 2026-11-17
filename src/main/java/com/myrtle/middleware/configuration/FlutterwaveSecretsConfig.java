package com.myrtle.middleware.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Setter
@Getter
@Component
@ConfigurationProperties(prefix = "flutterwave")
public class FlutterwaveSecretsConfig {
    private Map<String, String> secretKeys;
    private String webhookSecret;

    public String getKey(String productType) {
        return secretKeys.get(productType);
    }

}
