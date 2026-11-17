package com.myrtle.middleware.services.questionnaire;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.configuration.ScoringConfigLoader;
import com.myrtle.middleware.dtos.request.QuestionAnswer;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.dtos.response.QuestionnaireHistoryItem;
import com.myrtle.middleware.dtos.response.QuestionnaireResultResponse;
import com.myrtle.middleware.dtos.response.QuestionnaireSubmitResponse;
import com.myrtle.middleware.enums.WealthPersona;
import com.myrtle.middleware.exceptions.ResourceNotFound;
import com.myrtle.middleware.model.QuestionnaireResult;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.QuestionnaireResultRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationEngine;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult;
import com.myrtle.middleware.services.questionnaire.engine.ResponseSet;
import com.myrtle.middleware.services.questionnaire.report.ClientReportBuilder;
import com.myrtle.middleware.services.questionnaire.report.InternalReportBuilder;
import com.myrtle.middleware.utils.ResponseUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionnaireService {

    private static final String SUBMIT_MESSAGE = "Thank you for completing the Myrtle Client Discovery "
            + "Questionnaire. Your personalised summary is being prepared and will be delivered to your inbox shortly.";

    private final QuestionnaireResultRepository questionnaireResultRepository;
    private final UserRepository userRepository;
    private final QuestionnaireValidator validator;
    private final ScoringConfigLoader scoringConfigLoader;
    private final ClientReportBuilder clientReportBuilder;
    private final InternalReportBuilder internalReportBuilder;
    private final QuestionnaireReportService reportService;
    private final ObjectMapper objectMapper;
    private final ResponseUtil responseUtil;

    private final ClassificationEngine engine = new ClassificationEngine();

    public ResponseEntity<ApiResponse> submitAuthenticated(String email, List<QuestionAnswer> answers, HttpServletRequest request) {
        User user = userRepository.findByEmailAddress(email).orElseThrow(() -> new ResourceNotFound("User not found"));
        String fullName = (user.getFirstName() + " " + user.getLastName()).trim();
        return submit(user, user.getEmailAddress(), fullName, answers, resolveChannel(request));
    }

    public ResponseEntity<ApiResponse> submitWeb(String email, String fullName, List<QuestionAnswer> answers) {
        User user = userRepository.findByEmailAddress(email).orElse(null);
        return submit(user, email, fullName, answers, "WEB");
    }

    private ResponseEntity<ApiResponse> submit(User user, String email, String fullName, List<QuestionAnswer> answers, String channel) {
        QuestionnaireScoringConfig config = scoringConfigLoader.getConfig();

        List<String> errors = validator.validate(answers, config);
        if (!errors.isEmpty()) {
            log.info("Questionnaire validation failed | email={} | errors={}", email, errors.size());
            return responseUtil.badRequest("Questionnaire validation failed", errors);
        }

        ResponseSet responses = ResponseSet.from(answers);
        ClassificationResult result = engine.classify(responses, config);

        int version = questionnaireResultRepository.findMaxVersionByClientEmail(email) + 1;
        QuestionnaireResult entity = buildEntity(user, email, fullName, channel, version, answers, responses, result, config);
        questionnaireResultRepository.clearLatestFlag(email);
        questionnaireResultRepository.save(entity);

        log.info("Questionnaire classified and saved | email={} | version={} | persona={} | risk={} | compliance={}",
                email, version, result.persona().persona(), result.risk().profile(), result.compliance().status());

        reportService.polishAndDeliver(entity.getId());

        return responseUtil.success(SUBMIT_MESSAGE, new QuestionnaireSubmitResponse(
                entity.getId(), version,
                result.persona().displayLabel(),
                result.risk().profile().getDisplayName()));
    }

    public ResponseEntity<ApiResponse> getLatest(String email) {
        return questionnaireResultRepository
                .findFirstByClientEmailIgnoreCaseAndDeletedFalseOrderByVersionDesc(email)
                .map(entity -> responseUtil.success("Questionnaire result fetched successfully", toResponse(entity)))
                .orElseGet(() -> responseUtil.notFound("No questionnaire result found", null));
    }

    public ResponseEntity<ApiResponse> getHistory(String email) {
        List<QuestionnaireHistoryItem> history = questionnaireResultRepository
                .findByClientEmailIgnoreCaseAndDeletedFalseOrderByVersionDesc(email)
                .stream()
                .map(entity -> new QuestionnaireHistoryItem(
                        entity.getId(), entity.getVersion(), entity.isLatest(),
                        entity.getPersona().getDisplayName(),
                        entity.getRiskProfile().getDisplayName(),
                        entity.getCreatedAt()))
                .toList();
        return responseUtil.success("Questionnaire history fetched successfully", history);
    }

    private QuestionnaireResult buildEntity(User user, String email, String fullName, String channel,
                                            int version, List<QuestionAnswer> answers, ResponseSet responses,
                                            ClassificationResult result, QuestionnaireScoringConfig config) {
        QuestionnaireResult entity = new QuestionnaireResult();
        entity.setUser(user);
        entity.setClientEmail(email);
        entity.setClientName(fullName);
        entity.setChannel(channel);
        entity.setVersion(version);
        entity.setConfigVersion(config.configVersion());

        entity.setPersona(result.persona().persona());
        entity.setPersonaSecondary(result.persona().secondaryPersona());
        entity.setPersonaHybrid(result.persona().hybrid());
        entity.setPersonaOverrideApplied(result.persona().netWorthOverrideApplied());
        entity.setScoreEverydayBuilder(result.persona().scores().getOrDefault(WealthPersona.EVERYDAY_BUILDER, 0));
        entity.setScoreStrategicAchiever(result.persona().scores().getOrDefault(WealthPersona.STRATEGIC_ACHIEVER, 0));
        entity.setScoreLegacy(result.persona().scores().getOrDefault(WealthPersona.LEGACY_PRIVATE_WEALTH, 0));

        entity.setRiskProfile(result.risk().profile());
        entity.setRiskProfilePreOverride(result.risk().preOverrideProfile());
        entity.setRiskScore(result.risk().weightedScore());
        entity.setSuitabilityMismatch(result.risk().suitabilityMismatch());

        entity.setLiquidityNeed(result.liquidity().need());
        entity.setFinancialCapacity(result.capacity().category());
        entity.setCapacityScore(BigDecimal.valueOf(result.capacity().totalScore()));

        entity.setIncomeBand(answerText(responses, config, "Q11"));
        entity.setLiquidAssetsBand(answerText(responses, config, "Q13"));
        entity.setRealEstateBand(answerText(responses, config, "Q14"));
        entity.setBusinessAssetsBand(answerText(responses, config, "Q15"));
        entity.setDebtBand(answerText(responses, config, "Q16"));
        entity.setInvestmentHorizon(answerText(responses, config, "Q12"));
        entity.setInvestmentExperience(answerText(responses, config, "Q10"));

        entity.setDecisionStyle(result.decisionStyle().decisionStyle());
        entity.setCommunicationPreference(result.decisionStyle().communicationPreference());
        entity.setPrivacyOpenness(result.decisionStyle().privacyOpenness());

        entity.setComplianceStatus(result.compliance().status());
        entity.setUboStatus(answerText(responses, config, "Q22"));
        entity.setSourceOfFunds(responses.optionsOf("Q21").stream()
                .map(option -> config.optionLabel("Q21", option))
                .collect(Collectors.joining("; ")));

        entity.setResponsesJson(toJson(enrichAnswers(answers, config)));
        entity.setPersonaBreakdownJson(toJson(personaBreakdown(result.persona(), responses, config)));
        entity.setRiskBreakdownJson(toJson(riskBreakdown(result.risk(), responses, config)));
        entity.setCapacityBreakdownJson(toJson(capacityBreakdown(result.capacity(), responses, config)));
        entity.setLiquidityFlagsJson(toJson(result.liquidity()));
        entity.setComplianceFlagsJson(toJson(result.compliance()));
        entity.setMindsetThemesJson(toJson(result.decisionStyle().mindsetThemes()));

        entity.setClientReportHtml(clientReportBuilder.build(fullName, result));
        entity.setInternalReportHtml(internalReportBuilder.build(fullName, version, result, responses, config));
        return entity;
    }

    /**
     * Stores answers alongside their catalog labels so the raw record stays readable if codes ever change.
     */
    private List<Object> enrichAnswers(List<QuestionAnswer> answers, QuestionnaireScoringConfig config) {
        List<Object> enriched = new ArrayList<>();
        for (QuestionAnswer answer : answers) {
            String code = answer.questionCode() == null ? "" : answer.questionCode().trim().toUpperCase();
            String label = config.questionByCode(code)
                    .map(QuestionnaireScoringConfig.QuestionDef::label).orElse(null);
            List<String> optionLabels = answer.selectedOptions() == null ? List.of()
                    : answer.selectedOptions().stream()
                    .map(option -> config.optionLabel(code, option.trim().toUpperCase()))
                    .toList();
            enriched.add(new EnrichedAnswer(code, label, answer.selectedOptions(), optionLabels,
                    answer.primaryOption(), answer.freeText()));
        }
        return enriched;
    }

    public boolean isSurveyFilled(String email) {
        Optional<QuestionnaireResult> result = questionnaireResultRepository.findFirstByClientEmailIgnoreCaseAndDeletedFalseOrderByVersionDesc(email);
        return result.isPresent();
    }

    private record EnrichedAnswer(String questionCode, String questionLabel, List<String> selectedOptions,
                                  List<String> selectedOptionLabels, String primaryOption, String freeText) {
    }

    /**
     * Capacity breakdown with the client's answers spelled out in full
     * ("₦40m-₦100m" rather than "D") so the backoffice can render it directly.
     */
    private CapacityBreakdown capacityBreakdown(ClassificationResult.CapacityResult capacity,
                                                ResponseSet responses, QuestionnaireScoringConfig config) {
        List<CapacityBreakdownItem> items = new ArrayList<>();
        capacity.perQuestionScores().forEach((questionCode, score) -> {
            String question = config.questionByCode(questionCode)
                    .map(QuestionnaireScoringConfig.QuestionDef::label).orElse(questionCode);
            String response = responses.singleOption(questionCode)
                    .map(option -> config.optionLabel(questionCode, option))
                    .orElse(null);
            items.add(new CapacityBreakdownItem(questionCode, question, response, score));
        });
        return new CapacityBreakdown(items, capacity.totalScore(), capacity.category().getDisplayName());
    }

    private record CapacityBreakdownItem(String questionCode, String question, String response, double score) {
    }

    private record CapacityBreakdown(List<CapacityBreakdownItem> items, double totalScore, String category) {
    }

    /**
     * Risk breakdown with each scored area spelled out in full: the risk area
     * name, the question, the client's answer text, the score and its weight.
     */
    private RiskBreakdown riskBreakdown(ClassificationResult.RiskResult risk,
                                        ResponseSet responses, QuestionnaireScoringConfig config) {
        List<RiskBreakdownItem> items = new ArrayList<>();
        risk.perQuestionScores().forEach((questionCode, score) -> {
            String area = config.riskScoring().areaNames().getOrDefault(questionCode, questionCode);
            String question = config.questionByCode(questionCode)
                    .map(QuestionnaireScoringConfig.QuestionDef::label).orElse(questionCode);
            String response = responses.singleOption(questionCode)
                    .map(option -> config.optionLabel(questionCode, option))
                    .orElse(null);
            Double weight = config.riskScoring().weights().get(questionCode);
            items.add(new RiskBreakdownItem(questionCode, area, question, response, score,
                    weight == null ? 0 : weight));
        });

        List<AppliedCapDetail> caps = risk.appliedCaps().stream()
                .map(cap -> new AppliedCapDetail(
                        cap.trigger(),
                        selectionLabel(cap.trigger(), config),
                        cap.from().getDisplayName(),
                        cap.to().getDisplayName(),
                        cap.reason(),
                        cap.advisorOverridable()))
                .toList();

        return new RiskBreakdown(items, risk.weightedScore(),
                risk.preOverrideProfile().getDisplayName(),
                risk.profile().getDisplayName(),
                caps, risk.suitabilityMismatch());
    }

    private record RiskBreakdownItem(String questionCode, String area, String question, String response,
                                     int score, double weight) {
    }

    private record AppliedCapDetail(String trigger, String triggerResponse, String from, String to,
                                    String reason, boolean advisorOverridable) {
    }

    private record RiskBreakdown(List<RiskBreakdownItem> items, BigDecimal weightedScore,
                                 String profileBeforeOverrides, String profile,
                                 List<AppliedCapDetail> appliedCaps, boolean suitabilityMismatch) {
    }

    /**
     * Persona breakdown listing, per persona, the answers (in full text) that
     * contributed points - the audit trail behind the classification.
     */
    private PersonaBreakdown personaBreakdown(ClassificationResult.PersonaResult persona,
                                              ResponseSet responses, QuestionnaireScoringConfig config) {
        List<PersonaScoreDetail> scores = new ArrayList<>();
        persona.scores().forEach((wealthPersona, score) -> {
            List<ContributingResponse> contributing = config.personaScoring()
                    .getOrDefault(wealthPersona.name(), Map.of()).entrySet().stream()
                    .filter(entry -> responses.has(entry.getKey()))
                    .map(entry -> new ContributingResponse(
                            entry.getKey().substring(0, entry.getKey().indexOf('.')),
                            selectionLabel(entry.getKey(), config),
                            entry.getValue()))
                    .toList();
            scores.add(new PersonaScoreDetail(wealthPersona.getDisplayName(), score, contributing));
        });

        return new PersonaBreakdown(scores,
                persona.persona().getDisplayName(),
                persona.secondaryPersona() == null ? null : persona.secondaryPersona().getDisplayName(),
                persona.hybrid(),
                persona.netWorthOverrideApplied(),
                persona.reasons());
    }

    private record ContributingResponse(String questionCode, String response, int points) {
    }

    private record PersonaScoreDetail(String persona, int score, List<ContributingResponse> contributingResponses) {
    }

    private record PersonaBreakdown(List<PersonaScoreDetail> scores, String result, String secondaryPersona,
                                    boolean hybrid, boolean netWorthOverrideApplied, List<String> reasons) {
    }

    /**
     * Full option text of a single-select answer, e.g. Q11 "D" -> "₦40m-₦100m".
     */
    private String answerText(ResponseSet responses, QuestionnaireScoringConfig config, String questionCode) {
        return responses.singleOption(questionCode)
                .map(option -> config.optionLabel(questionCode, option))
                .orElse(null);
    }

    /**
     * Full option text for a "Qn.X" selection key.
     */
    private String selectionLabel(String selectionKey, QuestionnaireScoringConfig config) {
        int dot = selectionKey.indexOf('.');
        if (dot <= 0) {
            return selectionKey;
        }
        return config.optionLabel(selectionKey.substring(0, dot), selectionKey.substring(dot + 1));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Unable to serialize questionnaire data", e);
            return null;
        }
    }

    private QuestionnaireResultResponse toResponse(QuestionnaireResult entity) {
        List<String> themes;
        try {
            themes = entity.getMindsetThemesJson() == null ? List.of()
                    : objectMapper.readValue(entity.getMindsetThemesJson(), new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            themes = List.of();
        }
        return new QuestionnaireResultResponse(
                entity.getId(),
                entity.getVersion(),
                entity.getConfigVersion(),
                entity.getPersona().getDisplayName(),
                entity.getPersonaSecondary() == null ? null : entity.getPersonaSecondary().getDisplayName(),
                entity.isPersonaHybrid(),
                entity.getRiskProfile().getDisplayName(),
                entity.getRiskScore(),
                entity.getLiquidityNeed().getDisplayName(),
                entity.getFinancialCapacity().getDisplayName(),
                entity.getDecisionStyle(),
                entity.getCommunicationPreference(),
                themes,
                entity.getClientReportHtml(),
                entity.getCreatedAt());
    }

    private String resolveChannel(HttpServletRequest request) {
        if (request == null) {
            return "WEB";
        }
        String channel = request.getHeader("X-Client-Channel");
        if (channel != null && !channel.isBlank()) {
            return channel.trim().toUpperCase();
        }
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && (userAgent.contains("Android") || userAgent.contains("iOS")
                || userAgent.contains("Mobile"))) {
            return "MOBILE";
        }
        return "WEB";
    }
}
