package com.myrtle.middleware.configuration.security;

import com.myrtle.middleware.configuration.filter.AuthAuditFilter;
import com.myrtle.middleware.services.authentication.CustomerDetailsService;
import com.myrtle.middleware.utils.JwtAuthenticationFilter;
import com.myrtle.middleware.utils.MaskUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthAuditFilter authAuditFilter;
    private final CustomerDetailsService customerDetailsService;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .headers(headers -> headers
                        .httpStrictTransportSecurity(hsts -> hsts
                                .maxAgeInSeconds(31536000) // 1 year
                                .includeSubDomains(true)
                                .preload(true)
                        )
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers(HttpMethod.POST, "/api/v1/family-office-onboarding").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/send-otp",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/create-account",
                                "/api/v1/auth/verify-bvn",
                                "/api/v1/auth/reset-password",
                                "/api/v1/auth/webhook",
                                "/api/v1/auth/approve",
                                "/api/v1/auth/decline",
                                "/api/v1/auth/tier3/approve",
                                "/api/v1/auth/tier3/decline",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/corporate/login",
                                "/api/v1/auth/corporate/start/**",
                                "/api/v1/auth/corporate/validate-otp",
                                "/api/v1/auth/corporate/lookup",
                                "/api/v1/auth/create-corporate-user",

                                "/api/v1/auth/undelete-account",
                                "/api/v1/products/add-product",
                                "/api/v1/payments/callback",
                                "/api/v1/s3/upload-file",
                                "/api/v1/s3/upload-presigned-file",
                                "/api/v1/survey/web",
                                "/api/v1/questionnaire/web",
                                "/api/v1/payments/callback/**",
                                "/api/v1/investment/dollar-shield/**",
                                "/api/v1/investment/dollar-shield/approve/**",
                                "/api/v1/investment/dollar-shield/decline/**",
                                "/actuator/health",
                                "/api/v1/investment/tests",
                                "/api/v1/auth/add-bvn",
                                "/api/v1/auth/edit-main",
                                "/api/v1/manual-payment/destination-account/**",
                                "/api/v1/manual-payment/register-payment/**",
                                "/api/v1/manual-payment/get-status/**",
                                "/api/v1/refer/code-lookup",
                                "/health"
                        ).permitAll()

                        // Public careers feed: reads only, writes stay with the HR service
                        .requestMatchers(HttpMethod.GET, "/api/v1/job-listings", "/api/v1/job-listings/**").permitAll()

                        // Role-based endpoints
                        .requestMatchers("/api/v1/operations/**").hasRole("OPERATIONS")
                        .requestMatchers("/api/v1/customer-care/**").hasRole("CUSTOMER_CARE")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(requestLoggingFilter(), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(authAuditFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customerDetailsService);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public OncePerRequestFilter requestLoggingFilter() {
        Logger requestLogger = LoggerFactory.getLogger("RequestLogger");

        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain)
                    throws ServletException, IOException {

                long start = System.currentTimeMillis();
                try {
                    filterChain.doFilter(request, response);
                } finally {
                    if ("/health".equals(request.getRequestURI())) {
                        return;
                    }

                    long durationMs = System.currentTimeMillis() - start;

                    String email = "UNAUTHENTICATED USER";

                    var authentication =
                            SecurityContextHolder.getContext().getAuthentication();

                    if (authentication != null
                            && authentication.isAuthenticated()
                            && authentication.getPrincipal() != null) {

                        Object principal = authentication.getPrincipal();

                        if (principal instanceof UserDetails userDetails) {
                            email = userDetails.getUsername();
                        } else if (principal instanceof String principalString) {
                            email = principalString;
                        }
                    }

                    if (response.getStatus() != 200) {
                        requestLogger.info(
                                "Request {} {} -> {} in {} ms | user={}\n",
                                request.getMethod(),
                                request.getRequestURI(),
                                response.getStatus(),
                                durationMs,
                                maskEmail(email)
                        );
                    }
                }
            }
        };
    }


    private String maskEmail(String email) {
        if (email == null) return null;
        if (email.isBlank()) return "";

        int at = email.lastIndexOf('@');
        if (at < 0) {
            return email;
        }

        String pre = email.substring(0, Math.min(email.length(), 3));
        String post = email.substring(Math.max(0, at - 3));
        StringBuilder maskedMail = new StringBuilder();

        int hashLength = email.length() - pre.length() - post.length();
        maskedMail.repeat("*", Math.max(0, hashLength));

        return pre + maskedMail + post;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        //URLS
        configuration.setAllowedOrigins(List.of(
                "https://app.myrtleng.com",
                "https://dev-app.myrtleng.com",
                "https://www.myrtleng.com",
                "www.myrtleng.com",
                "https://myrtleng.com/",
                "http://localhost:5173"
        ));


        configuration.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"
        ));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "X-Auth-Token"
        ));
        configuration.setExposedHeaders(List.of("X-Auth-Token"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
