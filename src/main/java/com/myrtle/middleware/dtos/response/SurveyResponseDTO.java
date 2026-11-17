package com.myrtle.middleware.dtos.response;

import com.myrtle.middleware.dtos.request.SurveyRequest;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SurveyResponseDTO {
    private String persona;
    private String recommendation;
    private String channel;
    private List<SurveyRequest> responses;
}

