package com.myrtle.middleware.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

public final class QoreIdErrorParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private QoreIdErrorParser() {
    }

    public static String extractMessage(String responseBody, String fallback) {
        if (!StringUtils.hasText(responseBody)) {
            return fallback;
        }
        try {
            JsonNode node = MAPPER.readTree(responseBody);
            JsonNode message = node.get("message");
            if (message != null && !message.isNull() && StringUtils.hasText(message.asText())) {
                return message.asText();
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
