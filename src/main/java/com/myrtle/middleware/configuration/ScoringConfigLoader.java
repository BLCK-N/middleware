package com.myrtle.middleware.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads and validates questionnaire-scoring-config.json at startup.
 * Any structural problem (bad weights, non-ascending bands, scoring keys that
 * don't exist in the question catalog) aborts application startup so a broken
 * config can never silently misclassify clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScoringConfigLoader {

    static final String CONFIG_LOCATION = "questionnaire-scoring-config.json";
    private static final double WEIGHT_TOLERANCE = 0.001;

    private final ObjectMapper objectMapper;

    @Getter
    private QuestionnaireScoringConfig config;

    @PostConstruct
    void load() {
        this.config = loadAndValidate(objectMapper);
        log.info("Questionnaire scoring config loaded | version={} | questions={}",
                config.configVersion(), config.questions().size());
    }

    /** Static so tests can load the real config without a Spring context. */
    public static QuestionnaireScoringConfig loadAndValidate(ObjectMapper objectMapper) {
        QuestionnaireScoringConfig parsed;
        try (InputStream in = new ClassPathResource(CONFIG_LOCATION).getInputStream()) {
            parsed = objectMapper.readValue(in, QuestionnaireScoringConfig.class);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read " + CONFIG_LOCATION, e);
        }
        List<String> errors = validate(parsed);
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid questionnaire scoring config: " + String.join("; ", errors));
        }
        return parsed;
    }

    static List<String> validate(QuestionnaireScoringConfig cfg) {
        List<String> errors = new ArrayList<>();

        if (cfg.configVersion() == null || cfg.configVersion().isBlank()) {
            errors.add("configVersion is missing");
        }
        if (cfg.questions() == null || cfg.questions().isEmpty()) {
            errors.add("question catalog is empty");
            return errors;
        }

        double weightSum = cfg.riskScoring().weights().values().stream()
                .mapToDouble(Double::doubleValue).sum();
        if (Math.abs(weightSum - 1.0) > WEIGHT_TOLERANCE) {
            errors.add("risk weights sum to " + weightSum + ", expected 1.0");
        }

        validateAscendingBands(errors, "riskScoring.bands",
                cfg.riskScoring().bands().stream().map(QuestionnaireScoringConfig.RiskBand::maxInclusive).toList());
        validateAscendingBands(errors, "capacityScoring.bands",

                cfg.capacityScoring().bands().stream().map(QuestionnaireScoringConfig.CapacityBand::maxInclusive).toList());

        cfg.personaScoring().forEach((persona, table) ->
                table.keySet().forEach(key -> requireKey(errors, cfg, key, "personaScoring." + persona)));

        cfg.riskScoring().questionScores().forEach((question, scores) -> {
            if (cfg.questionByCode(question).isEmpty()) {
                errors.add("riskScoring.questionScores references unknown question " + question);
            } else {
                scores.keySet().forEach(option ->
                        requireKey(errors, cfg, question + "." + option, "riskScoring.questionScores"));
            }
            if (!cfg.riskScoring().weights().containsKey(question)) {
                errors.add("riskScoring.weights is missing an entry for " + question);
            }
        });
        cfg.riskScoring().weights().keySet().forEach(question -> {
            if (!cfg.riskScoring().questionScores().containsKey(question)) {
                errors.add("riskScoring.weights has weight for unscored question " + question);
            }
        });
        cfg.riskScoring().caps().forEach(cap -> requireKey(errors, cfg, cap.trigger(), "riskScoring.caps"));
        cfg.riskScoring().suitabilityMismatch().goalTriggers()
                .forEach(key -> requireKey(errors, cfg, key, "suitabilityMismatch.goalTriggers"));
        cfg.riskScoring().suitabilityMismatch().conflictTriggers()
                .forEach(key -> requireKey(errors, cfg, key, "suitabilityMismatch.conflictTriggers"));

        cfg.liquidity().mapping().keySet().forEach(key -> requireKey(errors, cfg, key, "liquidity.mapping"));
        cfg.liquidity().flags().forEach(flag ->
                flag.andAnyOf().forEach(key -> requireKey(errors, cfg, key, "liquidity.flags." + flag.code())));

        cfg.capacityScoring().questionScores().forEach((question, scores) -> {
            if (cfg.questionByCode(question).isEmpty()) {
                errors.add("capacityScoring.questionScores references unknown question " + question);
            } else {
                scores.keySet().forEach(option ->
                        requireKey(errors, cfg, question + "." + option, "capacityScoring.questionScores"));
            }
        });

        cfg.decisionStyle().forEach((question, mapping) -> {
            if (cfg.questionByCode(question).isEmpty()) {
                errors.add("decisionStyle references unknown question " + question);
            } else {
                mapping.keySet().forEach(option ->
                        requireKey(errors, cfg, question + "." + option, "decisionStyle." + question));
            }
        });
        cfg.mindsetThemes().keySet().forEach(key -> requireKey(errors, cfg, key, "mindsetThemes"));

        QuestionnaireScoringConfig.EnhancedTriggers enhanced = cfg.compliance().enhancedTriggers();
        enhanced.sourceOfFundsOptions().forEach(key -> requireKey(errors, cfg, key, "compliance.sourceOfFundsOptions"));
        enhanced.uboOptions().forEach(key -> requireKey(errors, cfg, key, "compliance.uboOptions"));
        enhanced.highDebtOptions().forEach(key -> requireKey(errors, cfg, key, "compliance.highDebtOptions"));
        requireKey(errors, cfg, cfg.compliance().holdTriggers().uboNoOption(), "compliance.holdTriggers.uboNoOption");

        return errors;
    }

    private static void validateAscendingBands(List<String> errors, String name, List<Double> maxValues) {
        if (maxValues.isEmpty()) {
            errors.add(name + " is empty");
            return;
        }
        for (int i = 1; i < maxValues.size(); i++) {
            if (maxValues.get(i) <= maxValues.get(i - 1)) {
                errors.add(name + " must be strictly ascending at index " + i);
            }
        }
    }

    private static void requireKey(List<String> errors, QuestionnaireScoringConfig cfg, String key, String where) {
        if (!cfg.selectionKeyExists(key)) {
            errors.add(where + " references unknown selection key " + key);
        }
    }
}
