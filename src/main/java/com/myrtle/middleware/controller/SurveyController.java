package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.SurveyRequest;
import com.myrtle.middleware.dtos.request.WebSurveyRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.survey.SurveyService;
import com.myrtle.middleware.utils.ValidatorUitls;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@Controller
@RestController
@RequestMapping(path = "api/v1/survey")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveyService;
    private final ValidatorUitls validatorUitls;

    @PostMapping("/submit-survey")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> submitSurvey(
            @RequestBody List<SurveyRequest> responses,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest request) {
        return surveyService.handleProcessSurvey(userDetails.getUsername(), responses, request);
    }

    @PostMapping("/web")
    public ResponseEntity<ApiResponse> submitSurveyWeb(
            @RequestBody WebSurveyRequest webSurveyRequest) {

        ResponseEntity<ApiResponse> errorResponse = validatorUitls.validateWebSurvey(webSurveyRequest);
        if (errorResponse != null) {
            return errorResponse;
        }

        return surveyService.handleWebSurvey(
                webSurveyRequest.email(),
                webSurveyRequest.gender(),
                webSurveyRequest.fullName(),
                webSurveyRequest.responses()
        );
    }

    @GetMapping("/get-survey")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getSurvey(
            @AuthenticationPrincipal UserDetails userDetails) {
        return surveyService.fetchUserSurveyResponse(userDetails.getUsername());
    }
}
