package com.myrtle.middleware.controller;

import com.myrtle.middleware.dtos.request.AddImage;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.services.authentication.AuthService;
import com.myrtle.middleware.services.s3.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RestController
@RequestMapping(path = "api/v1/files")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;
    private final AuthService authService;

    @PostMapping("/upload-file")
    public ResponseEntity<ApiResponse> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        return s3Service.uploadFile(file);
    }

//    @PostMapping("/upload-presigned-file")
//    public ResponseEntity<ApiResponse> presigned(@RequestParam("file") MultipartFile file) {
//        return s3Service.presigned(file);
//    }
//
//    @PostMapping("/add-file")
//    public ResponseEntity<ApiResponse> addFile(@RequestBody AddImage file, @AuthenticationPrincipal UserDetails userDetails) throws IOException {
//        return authService.addProfileImage(userDetails.getUsername(), file.url());
//    }

}
