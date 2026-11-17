package com.myrtle.middleware.services.questionnaire.engine;

import com.myrtle.middleware.configuration.QuestionnaireScoringConfig;
import com.myrtle.middleware.configuration.QuestionnaireScoringConfig.NetWorthOverride;
import com.myrtle.middleware.enums.WealthPersona;
import com.myrtle.middleware.services.questionnaire.engine.ClassificationResult.PersonaResult;

import java.util.*;

class PersonaClassifier {

    PersonaResult classify(ResponseSet responses, QuestionnaireScoringConfig config) {
        Map<WealthPersona, Integer> scores = score(responses, config);

        List<Map.Entry<WealthPersona, Integer>> ranked = scores.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<WealthPersona, Integer> e) -> e.getValue()).reversed()
                        .thenComparing(e -> e.getKey().ordinal()))
                .toList();

        WealthPersona top = ranked.get(0).getKey();
        WealthPersona second = ranked.get(1).getKey();
        boolean hybrid = ranked.get(0).getValue() - ranked.get(1).getValue()
                <= config.personaRules().hybridThreshold();

        List<String> reasons = new ArrayList<>();

        if (netWorthOverrideApplies(responses, config.personaRules().netWorthOverride())) {
            WealthPersona secondary = top == WealthPersona.LEGACY_PRIVATE_WEALTH ? second : top;
            reasons.add("Net worth override applied: high liquid assets, substantial real estate or "
                    + "business assets, and legacy/succession intent were all indicated.");
            addContributionReason(reasons, responses, config, WealthPersona.LEGACY_PRIVATE_WEALTH);
            return new PersonaResult(WealthPersona.LEGACY_PRIVATE_WEALTH, secondary, false, true, scores, reasons);
        }

        addContributionReason(reasons, responses, config, top);
        if (hybrid) {
            reasons.add("Top two persona scores are within " + config.personaRules().hybridThreshold()
                    + " points, indicating a hybrid orientation with " + second.getDisplayName() + ".");
        }
        return new PersonaResult(top, hybrid ? second : null, hybrid, false, scores, reasons);
    }

    private Map<WealthPersona, Integer> score(ResponseSet responses, QuestionnaireScoringConfig config) {
        Map<WealthPersona, Integer> scores = new EnumMap<>(WealthPersona.class);
        for (WealthPersona persona : WealthPersona.values()) {
            scores.put(persona, 0);
        }

        int primaryMultiplier = Math.max(1, config.personaRules().primaryOptionMultiplier());
        config.personaScoring().forEach((personaName, table) -> {
            WealthPersona persona = WealthPersona.valueOf(personaName);
            int total = 0;
            for (Map.Entry<String, Integer> entry : table.entrySet()) {
                if (!responses.has(entry.getKey())) {
                    continue;
                }
                int points = entry.getValue();
                if (primaryMultiplier > 1 && isPrimarySelection(responses, entry.getKey())) {
                    points *= primaryMultiplier;
                }
                total += points;
            }
            scores.put(persona, total);
        });
        return scores;
    }

    private boolean isPrimarySelection(ResponseSet responses, String selectionKey) {
        int dot = selectionKey.indexOf('.');
        String question = selectionKey.substring(0, dot);
        String option = selectionKey.substring(dot + 1);
        return responses.primaryOption(question).map(option::equals).orElse(false);
    }

    private boolean netWorthOverrideApplies(ResponseSet responses, NetWorthOverride rule) {
        return responses.hasAnyOf(rule.liquidAssetTriggers())
                && responses.hasAnyOf(rule.hardAssetTriggers())
                && responses.hasAnyOf(rule.legacyIntentTriggers());
    }

    private void addContributionReason(List<String> reasons, ResponseSet responses, QuestionnaireScoringConfig config, WealthPersona persona) {
        List<String> contributing = config.personaScoring()
                .getOrDefault(persona.name(), Map.of()).keySet().stream()
                .filter(responses::has)
                .map(key -> {
                    int dot = key.indexOf('.');
                    return config.optionLabel(key.substring(0, dot), key.substring(dot + 1));
                }).toList();

        if (!contributing.isEmpty()) {
            reasons.add("Client selected: " + String.join("; ", contributing) + ".");
        }
    }
}
