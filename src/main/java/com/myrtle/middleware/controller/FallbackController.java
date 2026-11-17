package com.myrtle.middleware.controller;

import lombok.RequiredArgsConstructor;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.utils.ResponseUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FallbackController {

    private final ResponseUtil responseUtil;

    @RequestMapping("/**")
    public ResponseEntity<ApiResponse> handleUnknown() {
        return responseUtil.success("Sorry.", "null");
    }
}
