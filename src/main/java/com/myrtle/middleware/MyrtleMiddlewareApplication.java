package com.myrtle.middleware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.http.client.HttpClientAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication(exclude = HttpClientAutoConfiguration.class)
public class MyrtleMiddlewareApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyrtleMiddlewareApplication.class, args);
    }
}
