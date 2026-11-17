package com.myrtle.middleware.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Controller
@RestController
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<?> healthCheck(@RequestParam(name = "testError", required = false) Boolean testError) {
        if (Boolean.TRUE.equals(testError)) {
            throw new RuntimeException("Sentry test error triggered via /health?testError=true");
        }
        return ResponseEntity.ok("Myrtle Middleware is running");
    }

}
