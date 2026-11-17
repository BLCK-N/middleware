package com.myrtle.middleware.services.survey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.exceptions.UserAlreadyExsists;
import com.myrtle.middleware.dtos.request.SurveyRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.SurveyResponseDTO;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.model.UserSurvey;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.repository.UserSurveyRepository;
import com.myrtle.middleware.services.EmailService;
import com.myrtle.middleware.utils.PersonaMapping;
import com.myrtle.middleware.utils.RecommendationGenerator;
import com.myrtle.middleware.utils.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SurveyService {

    private final UserSurveyRepository userSurveyRepository;
    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final ResponseUtil responseUtil;

    public ResponseEntity<ApiResponse> handleWebSurvey(String email, String gender, String fullName, List<SurveyRequest> responses) {
        log.info("Web survey submitted | email={} | fullName={}", email, fullName);
        return responseUtil.success("Submitted Successfully", processSurveyWeb(email, gender, fullName, responses));
    }

    public SurveyResponseDTO processSurveyWeb(String email, String gender, String fullName, List<SurveyRequest> responses) {
        log.debug("Processing web survey | email={} | responseCount={}", email, responses != null ? responses.size() : 0);
        Map<String, Double> scoreMap = calculatePersonaScores(responses);
        Map<String, Double> percentMap = calculatePercentages(scoreMap);
        String dominantPersona = classifyPersona(percentMap);
        String report = RecommendationGenerator.generateReport(fullName, dominantPersona);

        try {
            emailService.sendSurveyResponseMailWeb(
                    email,
                    fullName,
                    gender,
                    dominantPersona,
                    report,
                    percentMap.getOrDefault("Everyday Builder", Double.valueOf(0.0)),
                    percentMap.getOrDefault("Strategic Achiever", Double.valueOf(0.0)),
                    percentMap.getOrDefault("Private Wealth Niche", Double.valueOf(0.0))
            );

            log.info("Web survey processed | email={} | persona={}", email, dominantPersona);
            SurveyResponseDTO dto = new SurveyResponseDTO();
            dto.setPersona(dominantPersona);
            dto.setRecommendation(report);
            return dto;

        } catch (Exception e) {
            log.error("Web survey processing failed | email={} | reason={}", email, e.getMessage(), e);
            throw new UserAlreadyExsists("You've Filled the Survey Already");
        }
    }

    public ResponseEntity<ApiResponse> handleProcessSurvey(String email, List<SurveyRequest> responses, HttpServletRequest request) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        return responseUtil.success("Submitted Successfully", processSurvey(user, responses, resolveChannel(request)));
    }

    public ResponseEntity<ApiResponse> fetchUserSurveyResponse(String email) {
        User user = userRepository.findByEmailAddress(email).orElse(null);
        if (user == null) {
            return responseUtil.notFound("User not found", null);
        }

        UserSurvey userSurvey = userSurveyRepository.findByUser(user).orElse(null);
        if (userSurvey == null) {
            return responseUtil.notFound("User survey not found", null);
        }

        SurveyResponseDTO dto = new SurveyResponseDTO();
        dto.setPersona(userSurvey.getPersona());
        dto.setRecommendation(userSurvey.getRecommendation());
        dto.setChannel(userSurvey.getChannel());
        dto.setResponses(deserializeResponses(userSurvey.getResponseJson()));

        return responseUtil.success("User Survey Fetched Successfully", dto);
    }

    public ResponseEntity<ApiResponse> clearUserSurvey(String email) {
        User user = userRepository.findByEmailAddress(email)
                .orElseThrow(() -> new ResourceNotFound("User not found"));

        UserSurvey userSurvey = userSurveyRepository.findByUser(user).orElse(null);

        if (userSurvey == null) {
            return responseUtil.success("No survey data found for this user", Map.of("cleared", false));
        }

        userSurveyRepository.delete(userSurvey);

        return responseUtil.success("Survey data cleared successfully", Map.of("cleared", true));
    }

    public UserSurvey processSurvey(User user, List<SurveyRequest> responses, String channel) {
        log.debug("Processing survey | email={} | channel={} | responseCount={}", user.getEmailAddress(), channel, responses != null ? responses.size() : 0);

        Map<String, Double> scoreMap = calculatePersonaScores(responses);
        Map<String, Double> percentMap = calculatePercentages(scoreMap);
        String dominantPersona = classifyPersona(percentMap);
        String responseJson = serializeResponses(responses);
        String normalizedChannel = normalizeChannel(channel);

        String fullName = user.getFirstName() + " " + user.getLastName();
        String report = RecommendationGenerator.generateReport(fullName, dominantPersona);

        try {
            UserSurvey existingSurvey = userSurveyRepository.findByUser(user).orElse(null);

            if (existingSurvey != null) {
                existingSurvey.setPersona(dominantPersona);
                existingSurvey.setRecommendation(report);
                existingSurvey.setChannel(normalizedChannel);
                existingSurvey.setResponseJson(responseJson);
                return userSurveyRepository.save(existingSurvey);
            }

            UserSurvey survey = UserSurvey.builder()
                    .user(user)
                    .persona(dominantPersona)
                    .recommendation(report)
                    .channel(normalizedChannel)
                    .responseJson(responseJson)
                    .build();

            emailService.sendSurveyResponseMail(
                    user.getEmailAddress(),
                    user,
                    dominantPersona,
                    report
            );

            log.info("Survey saved | email={} | persona={} | channel={}", user.getEmailAddress(), dominantPersona, normalizedChannel);
            return userSurveyRepository.save(survey);

        } catch (Exception e) {
            log.error("Survey processing failed | email={} | reason={}", user.getEmailAddress(), e.getMessage(), e);
            throw new UserAlreadyExsists("You've Filled the Survey Already");
        }
    }

    private String serializeResponses(List<SurveyRequest> responses) {
        if (responses == null || responses.isEmpty()) {
            throw new IllegalArgumentException("Survey responses must not be null or empty");
        }
        try {
            return objectMapper.writeValueAsString(responses);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to serialize survey responses", e);
        }
    }

    private List<SurveyRequest> deserializeResponses(String responseJson) {
        if (responseJson == null || responseJson.isBlank()) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(responseJson, new TypeReference<List<SurveyRequest>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String resolveChannel(HttpServletRequest request) {
        if (request == null) {
            return "WEB";
        }

        String channel = request.getHeader("X-Client-Channel");
        if (channel != null && !channel.isBlank()) {
            return normalizeChannel(channel);
        }

        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && (userAgent.contains("Android") || userAgent.contains("iOS") || userAgent.contains("Mobile"))) {
            return "MOBILE";
        }

        return "WEB";
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return "WEB";
        }

        return channel.trim().toUpperCase();
    }

    private String normalize(String text) {
        return PersonaMapping.normalize(text);
    }

    private Map<String, Double> calculatePersonaScores(List<SurveyRequest> responses) {

        Map<String, Double> scoreMap = new HashMap<>();
        scoreMap.put("Strategic Achiever", 0.0);
        scoreMap.put("Everyday Builder", 0.0);
        scoreMap.put("Private Wealth Niche", 0.0);

        Map<String, Map<String, String>> mappings = PersonaMapping.getQuestionMappings();

        for (SurveyRequest r : responses) {

            String question = normalize(r.question());
            String option = r.optionSelected();

            if (question == null || option == null) continue;

            option = option.trim().toUpperCase();

            if (!mappings.containsKey(question)) continue;

            Map<String, String> questionMap = mappings.get(question);

            if (!questionMap.containsKey(option)) continue;

            String persona = questionMap.get(option);
            if (scoreMap.containsKey(persona)) {
                scoreMap.put(persona, scoreMap.get(persona) + 1);
            }
        }

        return scoreMap;
    }

    private Map<String, Double> calculatePercentages(Map<String, Double> scoreMap) {
        double totalScoredQuestions = PersonaMapping.getQuestionMappings().size();

        Map<String, Double> percentMap = new HashMap<>();

        scoreMap.forEach((k, v) -> {
            double percent = totalScoredQuestions == 0 ? 0 : (v / totalScoredQuestions) * 100;
            percentMap.put(k, Math.round(percent * 100.0) / 100.0);
        });

        return percentMap;
    }

    private String classifyPersona(Map<String, Double> percentMap) {

        List<Map.Entry<String, Double>> sorted = percentMap.entrySet()
                .stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .toList();

        var top1 = sorted.get(0);
        var top2 = sorted.get(1);

        double diff = Math.abs(top1.getValue() - top2.getValue());

        return (diff <= 10)
                ? top1.getKey() + " + " + top2.getKey()
                : top1.getKey();
    }
}
