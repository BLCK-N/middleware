package com.myrtle.middleware.services.s3;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.utils.ResponseUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final AmazonS3 amazonS3;
    private final S3Client s3Client;
    private final ResponseUtil responseUtil;

    @Value("${aws.bucket.name}")
    private String bucketName;

    @Value("${aws.region}")
    private String region;

    public ResponseEntity<ApiResponse> uploadFile(MultipartFile file) throws IOException {

        String key = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        key = key.replaceAll("\\s+", "_");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .build();
        log.info("putObjectRequest: {}", putObjectRequest);
        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));

        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8).replace("+", "%20");
        String format = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, encodedKey);
        log.info("format: {}", format);
        return responseUtil.accepted("File Uploaded Successfully", format);
    }

    public ResponseEntity<ApiResponse> presigned(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return responseUtil.badRequest("File is required", null);
        }

        // Extract extension safely
        String originalName = file.getOriginalFilename();
        String extension = "";

        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }

        // Generate unique S3 key
        String s3Key = "uploads/" + UUID.randomUUID() + extension;

        // Expiry time
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 2);

        String presignedUrl = amazonS3.generatePresignedUrl(
                bucketName,
                s3Key,
                calendar.getTime(),
                HttpMethod.PUT
        ).toString();

        // Return both URL and key
        Map<String, Object> data = new HashMap<>();
        data.put("uploadUrl", presignedUrl);
        data.put("fileKey", s3Key);

        return responseUtil.success("Pre-signed upload URL generated successfully", data);
    }


}
