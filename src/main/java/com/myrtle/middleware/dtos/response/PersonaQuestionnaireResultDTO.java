package com.myrtle.middleware.dtos.response;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class PersonaQuestionnaireResultDTO {

    private Map<String, Integer> scores;
    private Map<String, Integer> percentages;
    private String primaryPersona;
    private String secondaryPersona;
    private boolean isHybrid;
    private String finalPersonaName;
    private String explanation;
    private Map<String, String> openEndedResponses;
    private int totalScoredQuestions;
}
