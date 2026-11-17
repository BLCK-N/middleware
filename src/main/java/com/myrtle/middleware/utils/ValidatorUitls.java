package com.myrtle.middleware.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myrtle.middleware.dtos.request.SurveyRequest;
import com.myrtle.middleware.dtos.request.WebSurveyRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ValidatorUitls {
    private static final Map<String, String> NIGERIAN_STATE_CODE_BY_NAME = new LinkedHashMap<>(Map.ofEntries(
            Map.entry("abia", "AB"),
            Map.entry("adamawa", "AD"),
            Map.entry("akwa ibom", "AK"),
            Map.entry("anambra", "AN"),
            Map.entry("bauchi", "BA"),
            Map.entry("benue", "BE"),
            Map.entry("borno", "BO"),
            Map.entry("bayelsa", "BY"),
            Map.entry("cross river", "CR"),
            Map.entry("delta", "DE"),
            Map.entry("ebonyi", "EB"),
            Map.entry("edo", "ED"),
            Map.entry("ekiti", "EK"),
            Map.entry("enugu", "EN"),
            Map.entry("abuja capital territory", "FC"),
            Map.entry("gombe", "GO"),
            Map.entry("imo", "IM"),
            Map.entry("jigawa", "JI"),
            Map.entry("kaduna", "KD"),
            Map.entry("kebbi", "KE"),
            Map.entry("kano", "KN"),
            Map.entry("kogi", "KO"),
            Map.entry("katsina", "KT"),
            Map.entry("kwara", "KW"),
            Map.entry("lagos", "LA"),
            Map.entry("nassarawa", "NA"),
            Map.entry("niger", "NI"),
            Map.entry("ogun", "OG"),
            Map.entry("ondo", "ON"),
            Map.entry("osun", "OS"),
            Map.entry("oyo", "OY"),
            Map.entry("plateau", "PL"),
            Map.entry("rivers", "RI"),
            Map.entry("sokoto", "SO"),
            Map.entry("taraba", "TA"),
            Map.entry("yobe", "YO"),
            Map.entry("zamfara", "ZA")
    ));
    public static final Map<String, String> NIGERIAN_STATE_NAME_BY_CODE = Map.ofEntries(
            Map.entry("AB", "Abia"),
            Map.entry("AD", "Adamawa"),
            Map.entry("AK", "Akwa Ibom"),
            Map.entry("AN", "Anambra"),
            Map.entry("BA", "Bauchi"),
            Map.entry("BE", "Benue"),
            Map.entry("BO", "Borno"),
            Map.entry("BY", "Bayelsa"),
            Map.entry("CR", "Cross River"),
            Map.entry("DE", "Delta"),
            Map.entry("EB", "Ebonyi"),
            Map.entry("ED", "Edo"),
            Map.entry("EK", "Ekiti"),
            Map.entry("EN", "Enugu"),
            Map.entry("FC", "Abuja Capital Territory"),
            Map.entry("GO", "Gombe"),
            Map.entry("IM", "Imo"),
            Map.entry("JI", "Jigawa"),
            Map.entry("KD", "Kaduna"),
            Map.entry("KE", "Kebbi"),
            Map.entry("KN", "Kano"),
            Map.entry("KO", "Kogi"),
            Map.entry("KT", "Katsina"),
            Map.entry("KW", "Kwara"),
            Map.entry("LA", "Lagos"),
            Map.entry("NA", "Nassarawa"),
            Map.entry("NI", "Niger"),
            Map.entry("OG", "Ogun"),
            Map.entry("ON", "Ondo"),
            Map.entry("OS", "Osun"),
            Map.entry("OY", "Oyo"),
            Map.entry("PL", "Plateau"),
            Map.entry("RI", "Rivers"),
            Map.entry("SO", "Sokoto"),
            Map.entry("TA", "Taraba"),
            Map.entry("YO", "Yobe"),
            Map.entry("ZA", "Zamfara")
    );

    private final ResponseUtil responseUtil;

    public ValidatorUitls(ResponseUtil responseUtil) {
        this.responseUtil = responseUtil;
    }

    /**
     * @return ResponseEntity with error if validation fails, null if validation passed
     */
    public ResponseEntity<ApiResponse> validateWebSurvey(WebSurveyRequest req) {

        if (req == null) {
            return responseUtil.badRequest("Request body cannot be null", Map.of("error", "Request body cannot be null"));
        }

        if (req.email() == null || req.email().trim().isEmpty()) {
            return responseUtil.badRequest("email is required", Map.of("error", "email is required"));
        }

        if (req.fullName() == null || req.fullName().trim().isEmpty()) {
            return responseUtil.badRequest("fullName is required", Map.of("error", "fullName is required"));
        }

        if (req.gender() == null || req.gender().trim().isEmpty()) {
            return responseUtil.badRequest("Gender is required", Map.of("error", "Gender is required"));
        }

        if (req.responses() == null || req.responses().isEmpty()) {
            return responseUtil.badRequest("responses cannot be empty", Map.of("error", "responses cannot be empty"));
        }

        // Validate each response item
        for (SurveyRequest r : req.responses()) {
            if (r == null) {
                return responseUtil.badRequest("survey item cannot be null", Map.of("error", "survey item cannot be null"));
            }
            if (r.question() == null || r.question().trim().isEmpty()) {
                return responseUtil.badRequest("question is required", Map.of("error", "question is required"));
            }
            if (r.answer() == null || r.answer().trim().isEmpty()) {
                return responseUtil.badRequest("answer is required", Map.of("error", "answer is required"));
            }
            if (r.optionSelected() == null) {
                return responseUtil.badRequest("optionSelected cannot be null", Map.of("error", "optionSelected cannot be null"));
            }
        }

        return null; // means validation passed
    }

    public static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    public boolean isEmail(String identifier) {
        return identifier != null && EMAIL_PATTERN.matcher(identifier).matches();
    }

    public boolean isImageUrl(String url) {
        String lowerCaseUrl = url.toLowerCase();
        return lowerCaseUrl.endsWith(".jpg")
                || lowerCaseUrl.endsWith(".jpeg")
                || lowerCaseUrl.endsWith(".png")
                || lowerCaseUrl.endsWith(".gif")
                || lowerCaseUrl.endsWith(".webp");
    }

    private static final Set<String> MASKED_FIELDS = Set.of("password", "pin");
    private static final String MASK = "***";
    private static final ObjectMapper MASKING_MAPPER = new ObjectMapper();

    public String maskSensitiveFields(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }

        try {
            JsonNode root = MASKING_MAPPER.readTree(json);
            maskNode(root);
            return MASKING_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            // Failing closed: returning the input could publish the very values we are masking.
            return "[unparseable json - redacted]";
        }
    }

    private void maskNode(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            List<String> fieldNames = new ArrayList<>();
            objectNode.fieldNames().forEachRemaining(fieldNames::add);

            for (String fieldName : fieldNames) {
                if (MASKED_FIELDS.contains(fieldName.toLowerCase(Locale.ROOT))) {
                    JsonNode value = objectNode.get(fieldName);
                    if (value != null && !value.isNull()) {
                        objectNode.put(fieldName, MASK);
                    }
                } else {
                    maskNode(objectNode.get(fieldName));
                }
            }
        } else if (node != null && node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    public String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public Long toLongOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String digits = value.replaceAll("\\D", "");
        if (digits.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public String normalizeGenderValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("M".equals(normalized)) {
            return "MALE";
        }
        if ("F".equals(normalized)) {
            return "FEMALE";
        }
        return normalized;
    }

    public String resolveNigerianStateCode(String stateOfOrigin, String residentialAddress) {
        String normalizedState = normalizeStateKey(stateOfOrigin);

        if (normalizedState != null) {
            String codeFromState = NIGERIAN_STATE_CODE_BY_NAME.get(normalizedState);
            if (codeFromState != null) {
                return codeFromState;
            }
        }

        String normalizedAddress = normalizeStateKey(residentialAddress);
        if (normalizedAddress != null) {
            for (Map.Entry<String, String> entry : NIGERIAN_STATE_CODE_BY_NAME.entrySet()) {
                if (normalizedAddress.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }

        return "LA";
    }

    private String normalizeStateKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.toLowerCase(Locale.ROOT)
                .replace("federal capital territory", "abuja capital territory")
                .replace("abuja fct", "abuja capital territory")
                .replace("fct", "abuja capital territory")
                .replace("nasarawa", "nassarawa")
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        return normalized.isEmpty() ? null : normalized;
    }

    public String resolveNigerianStateName(String stateOfOrigin, String residentialAddress) {
        String code = resolveNigerianStateCode(stateOfOrigin, residentialAddress);
        return ValidatorUitls.NIGERIAN_STATE_NAME_BY_CODE.getOrDefault(code, "Lagos");
    }
}
