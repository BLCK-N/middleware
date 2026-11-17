package com.myrtle.middleware.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.dtos.response.ExternalValidationErrorResponse;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses MyWealth client error bodies into frontend-friendly API payloads.
 */
public final class MyWealthErrorParser {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern DETAIL_FIELD_PATTERN = Pattern.compile("([^:,]+): \\{([^}]+)\\}");

    private MyWealthErrorParser() {
    }

    public record ParsedError(
            String userMessage,
            ExternalValidationErrorResponse error,
            int upstreamStatus
    ) {
    }

    public static ParsedError parseClientError(int upstreamStatus, String responseBody) {
        ExternalValidationErrorResponse error = parseResponseBody(responseBody);
        enrichFieldsFromDetails(error);
        return new ParsedError(toUserMessage(error), error, upstreamStatus);
    }

    public static Map<String, Object> toApiData(ParsedError parsed) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("provider", "MyWealth");
        data.put("upstreamStatus", parsed.upstreamStatus());
        data.put("summary", blankToNull(parsed.error().getSummary()));
        data.put("code", blankToNull(parsed.error().getCode()));
        data.put("details", blankToNull(parsed.error().getDetails()));
        data.put("fields", parsed.error().getFields() == null ? List.of() : parsed.error().getFields());
        return data;
    }

    static ExternalValidationErrorResponse parseResponseBody(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            ExternalValidationErrorResponse fallback = new ExternalValidationErrorResponse();
            fallback.setSummary("Request rejected");
            fallback.setFields(List.of());
            return fallback;
        }

        try {
            ExternalValidationErrorResponse parsed = MAPPER.readValue(responseBody, ExternalValidationErrorResponse.class);
            if (parsed.getFields() == null) {
                parsed.setFields(List.of());
            }
            return parsed;
        } catch (Exception ignored) {
            ExternalValidationErrorResponse fallback = new ExternalValidationErrorResponse();
            fallback.setSummary("Request rejected");
            fallback.setDetails(responseBody.trim());
            fallback.setFields(List.of());
            return fallback;
        }
    }

    static void enrichFieldsFromDetails(ExternalValidationErrorResponse error) {
        if (error.getFields() != null && !error.getFields().isEmpty()) {
            return;
        }
        List<ExternalValidationErrorResponse.FieldError> parsed = parseFieldsFromDetails(error.getDetails());
        if (!parsed.isEmpty()) {
            error.setFields(parsed);
        }
    }

    static List<ExternalValidationErrorResponse.FieldError> parseFieldsFromDetails(String details) {
        if (!StringUtils.hasText(details)) {
            return List.of();
        }

        List<ExternalValidationErrorResponse.FieldError> fields = new ArrayList<>();
        Matcher matcher = DETAIL_FIELD_PATTERN.matcher(details);
        while (matcher.find()) {
            ExternalValidationErrorResponse.FieldError field = new ExternalValidationErrorResponse.FieldError();
            field.setProperty(matcher.group(1).trim());
            field.setMessage(matcher.group(2).trim());
            fields.add(field);
        }
        return fields;
    }

    static String toUserMessage(ExternalValidationErrorResponse error) {
        String details = humanizeDetails(error.getDetails());
        if (StringUtils.hasText(details)) {
            if ("Validation Failed".equalsIgnoreCase(error.getSummary())
                    && error.getFields() != null
                    && !error.getFields().isEmpty()) {
                ExternalValidationErrorResponse.FieldError first = error.getFields().get(0);
                return "Validation failed: " + first.getProperty() + " " + first.getMessage();
            }
            return details;
        }

        if (StringUtils.hasText(error.getSummary())) {
            return error.getSummary();
        }

        return "MyWealth rejected the corporate client request";
    }

    private static String humanizeDetails(String details) {
        if (!StringUtils.hasText(details)) {
            return null;
        }
        return details.trim().replaceAll("\\{([^}]+)\\}", "$1");
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
