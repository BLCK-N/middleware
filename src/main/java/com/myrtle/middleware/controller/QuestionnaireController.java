package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.QuestionnaireSubmissionRequest;
import com.myrtle.middleware.dtos.request.WebQuestionnaireRequest;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.questionnaire.QuestionnaireService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "api/v1/questionnaire")
@RequiredArgsConstructor
public class QuestionnaireController {

    private final QuestionnaireService questionnaireService;

    @PostMapping("/submit")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> submit(@Valid @RequestBody QuestionnaireSubmissionRequest submissionRequest,
                                              @AuthenticationPrincipal UserDetails userDetails, HttpServletRequest request) {
        return questionnaireService.submitAuthenticated(userDetails.getUsername(), submissionRequest.answers(), request);
    }

    @PostMapping("/web")
    public ResponseEntity<ApiResponse> submitWeb(@Valid @RequestBody WebQuestionnaireRequest webRequest) {
        return questionnaireService.submitWeb(webRequest.email(), webRequest.fullName(), webRequest.answers());
    }

    @GetMapping("/latest")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getLatest(@AuthenticationPrincipal UserDetails userDetails) {
        return questionnaireService.getLatest(userDetails.getUsername());
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getHistory(@AuthenticationPrincipal UserDetails userDetails) {
        return questionnaireService.getHistory(userDetails.getUsername());
    }
}
