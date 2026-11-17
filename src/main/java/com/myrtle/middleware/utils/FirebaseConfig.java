package com.myrtle.middleware.utils;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @PostConstruct
    public void init() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) return;

        GoogleCredentials credentials;
        Resource serviceAccount = new ClassPathResource("firebase-service-account.json");

        if (serviceAccount.exists()) {
            try (InputStream input = serviceAccount.getInputStream()) {
                credentials = GoogleCredentials.fromStream(input);
            }
        } else {
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        FirebaseApp.initializeApp(options);
    }
}
