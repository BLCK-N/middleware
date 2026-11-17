package com.myrtle.middleware.utils;

import com.myrtle.middleware.dtos.response.ApiResponse;
import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.PendingCorporateDataRepository;
import com.myrtle.middleware.services.authentication.CustomerDetailsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myrtle.middleware.security.AuthenticatedAccountPrincipal;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final CustomerDetailsService customerDetailsService;
    private final CorporateClientRepository corporateClientRepository;
    private final PendingCorporateDataRepository pendingCorporateDataRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/api/v1/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // A stale token from a logged-in session must not fail the public careers feed
        if (isPublicJobListingRead(request) || isRefreshRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            String userEmail = jwtUtils.extractUsername(jwt);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                var userDetails = loadPrincipal(jwt, userEmail);
                if (jwtUtils.validateToken(jwt, userDetails)) {
                    var authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    sendErrorResponse(response, "Account is disabled or deleted", HttpStatus.UNAUTHORIZED);
                    return;
                }
            }


            // Only proceed if no exception occurred
            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            sendErrorResponse(response, "Token has expired", HttpStatus.UNAUTHORIZED);
        } catch (UsernameNotFoundException e) {
            sendErrorResponse(response, "Authenticated account was not found", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            sendErrorResponse(response, "Malformed or invalid token", HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            sendErrorResponse(response, "Authentication failed. Please try again later", HttpStatus.BAD_REQUEST);
        }
    }

    private boolean isPublicJobListingRead(HttpServletRequest request) {
        return HttpMethod.GET.matches(request.getMethod())
                && request.getRequestURI().startsWith("/api/v1/job-listings");
    }

    private boolean isRefreshRequest(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && request.getRequestURI().endsWith("/api/v1/auth/refresh");
    }

    private void sendErrorResponse(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        if (response.isCommitted()) {
            return; // Prevent writing twice
        }

        response.resetBuffer(); // Clear anything already written
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse apiResponse = new ApiResponse(false, message, null);

        // Use OutputStream consistently
        response.getOutputStream().write(objectMapper.writeValueAsBytes(apiResponse));
        response.flushBuffer(); // commit the response
    }

    private AuthenticatedAccountPrincipal loadPrincipal(String jwt, String email) {
        AccountType accountType = jwtUtils.extractAccountType(jwt);
        if (accountType == null) {
            accountType = AccountType.USER;
        }

        return switch (accountType) {
            case USER -> loadUserPrincipal(email);
            case CORPORATE_ACCOUNT -> loadCorporateAccountPrincipal(jwt, email);
            case PENDING_CORPORATE_ONBOARDING -> loadPendingCorporateOnboardingPrincipal(jwt, email);
            case CORPORATE_USER -> throw new UsernameNotFoundException(
                    "Corporate user authentication is not available in this build"
            );
        };
    }

    private AuthenticatedAccountPrincipal loadUserPrincipal(String email) {
        var userDetails = customerDetailsService.loadUserByUsername(email);
        if (userDetails instanceof User user) {
            return AuthenticatedAccountPrincipal.fromUser(user);
        }
        throw new UsernameNotFoundException("User not found with email: " + email);
    }

    private AuthenticatedAccountPrincipal loadCorporateAccountPrincipal(String jwt, String email) {
        String id = jwtUtils.extractId(jwt);

        Optional<CorporateClient> corporateClient = Optional.empty();
        if (id != null && !id.isBlank()) {
            corporateClient = corporateClientRepository.findById(id);
        }
        if (corporateClient.isEmpty()) {
            corporateClient = corporateClientRepository.findByEmailIgnoreCase(email);
        }

        CorporateClient client = corporateClient
                .orElseThrow(() -> new UsernameNotFoundException("Corporate account not found with email: " + email));

        Role role = client.getRole() == null ? Role.CORPORATE_ACCOUNT : client.getRole();
        if (role != Role.CORPORATE_ACCOUNT) {
            throw new UsernameNotFoundException("Corporate account role is invalid");
        }

        return AuthenticatedAccountPrincipal.fromCorporateClient(client);
    }

    private AuthenticatedAccountPrincipal loadPendingCorporateOnboardingPrincipal(String jwt, String email) {
        String id = jwtUtils.extractId(jwt);

        Optional<PendingCorporateData> pendingCorporateData = Optional.empty();
        if (id != null && !id.isBlank()) {
            pendingCorporateData = pendingCorporateDataRepository.findById(id);
        }
        if (pendingCorporateData.isEmpty()) {
            pendingCorporateData = pendingCorporateDataRepository.findByEmailIgnoreCase(email);
        }

        PendingCorporateData pending = pendingCorporateData
                .orElseThrow(() -> new UsernameNotFoundException("Pending corporate onboarding not found for email: " + email));

        if (!pending.isEmailVerified()) {
            throw new UsernameNotFoundException("Corporate onboarding email is not verified for: " + email);
        }

        return AuthenticatedAccountPrincipal.fromPendingCorporateData(pending);
    }

}
