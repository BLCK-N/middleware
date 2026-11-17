package com.myrtle.middleware.utils;

import com.myrtle.middleware.enums.AccountType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.PendingCorporateData;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.security.AuthenticatedAccountPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;

@Component
@RequiredArgsConstructor
public class JwtUtils {

    private final UserRepository userRepository;

    @Value("${jwt.secret}")
    private String secret;

    private static final String CORPORATE_ATTACH_PURPOSE = "CORPORATE_USER_ATTACH";
    private static final long CORPORATE_ATTACH_EXPIRATION = 1000L * 60 * 15; // 15 minutes

    private Key getSigningKey() {
        try {
            // If secret is Base64 encoded
            byte[] keyBytes = Base64.getDecoder().decode(secret);
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (IllegalArgumentException e) {
            // If secret is plain text, make sure it's >=32 chars
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    // Generate token only if user is active
    public String generateToken(String email) {
        String normalizedEmail = normalizeEmail(email);
        User user = userRepository.findByEmailAddress(normalizedEmail)
                .orElseThrow(() -> new IllegalStateException("User not found"));

        return generateToken(user);
    }

    public String generateToken(User user) {
        if (user.isDeleted() || user.isDisabled()) {
            throw new IllegalStateException("Account is disabled or deleted. Cannot generate token.");
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("email", user.getEmailAddress());
        claims.put("role", safeRole(user.getRole(), Role.USER).name());
        claims.put("tier", user.getTier());
        claims.put("accountType", AccountType.USER.name());

        return buildToken(user.getEmailAddress(), claims);
    }

    public String generateToken(CorporateClient corporateClient) {
        Role role = safeRole(corporateClient.getRole(), Role.CORPORATE_ACCOUNT);
        Integer tier = corporateClient.getTier() == null ? 3 : corporateClient.getTier();

        Map<String, Object> claims = new HashMap<>();
        claims.put("id", corporateClient.getId());
        claims.put("email", corporateClient.getEmail());
        claims.put("role", role.name());
        claims.put("tier", tier);
        claims.put("accountType", AccountType.CORPORATE_ACCOUNT.name());
        claims.put("corporateClientId", corporateClient.getId());

        return buildToken(corporateClient.getEmail(), claims);
    }

    public String generatePendingCorporateOnboardingToken(PendingCorporateData pendingCorporateData) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", pendingCorporateData.getId());
        claims.put("email", pendingCorporateData.getEmail());
        claims.put("role", Role.USER.name());
        claims.put("accountType", AccountType.PENDING_CORPORATE_ONBOARDING.name());

        return buildToken(pendingCorporateData.getEmail(), claims);
    }

    /**
     * Short-lived token proving the caller supplied a matching RC number + registered BVN pair.
     * It is passed in the request body of corporate-user creation, never as an Authorization
     * header, so it grants no session of its own.
     */
    public String generateCorporateAttachToken(CorporateClient corporateClient) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("corporateClientId", corporateClient.getId());
        claims.put("rcNumber", corporateClient.getRcNumber());
        claims.put("purpose", CORPORATE_ATTACH_PURPOSE);

        return Jwts.builder()
                .setSubject(corporateClient.getId())
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + CORPORATE_ATTACH_EXPIRATION))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * @return the corporate client id carried by a valid, unexpired attach token
     * @throws IllegalArgumentException if the token is not an attach token
     */
    public String extractCorporateAttachClientId(String token) {
        Claims claims = getAllClaimsFromToken(token);

        if (!CORPORATE_ATTACH_PURPOSE.equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Token is not a corporate attach token");
        }
        if (claims.getExpiration() == null || claims.getExpiration().before(new Date())) {
            throw new IllegalArgumentException("Corporate attach token has expired");
        }

        String corporateClientId = claims.get("corporateClientId", String.class);
        if (corporateClientId == null || corporateClientId.isBlank()) {
            throw new IllegalArgumentException("Corporate attach token is missing the corporate client id");
        }
        return corporateClientId;
    }

    public long corporateAttachTokenTtlSeconds() {
        return CORPORATE_ATTACH_EXPIRATION / 1000L;
    }

    public String extractUsername(String token) {
        return getAllClaimsFromToken(token).getSubject();
    }

    public String extractId(String token) {
        return getAllClaimsFromToken(token).get("id", String.class);
    }

    public String extractCorporateClientId(String token) {
        return getAllClaimsFromToken(token).get("corporateClientId", String.class);
    }

    public Role extractRole(String token) {
        String role = getAllClaimsFromToken(token).get("role", String.class);
        return role == null ? null : Role.valueOf(role);
    }

    public AccountType extractAccountType(String token) {
        String accountType = getAllClaimsFromToken(token).get("accountType", String.class);
        return accountType == null ? null : AccountType.valueOf(accountType);
    }

    // Also validate that user is active
    public boolean validateToken(String token, UserDetails userDetails) {
        final String username = normalizeEmail(extractUsername(token));
        boolean notExpired = !isTokenExpired(token);

        if (!username.equals(normalizeEmail(userDetails.getUsername())) || !notExpired) {
            return false;
        }

        if (userDetails instanceof AuthenticatedAccountPrincipal principal) {
            if (principal.getAccountType() == AccountType.PENDING_CORPORATE_ONBOARDING) {
                return principal.isEnabled() && notExpired;
            }
            return principal.isEnabled();
        }

        // Check user status directly from DB
        User user = userRepository.findByEmailAddress(username)
                .orElse(null);

        return user != null && !user.isDeleted() && !user.isDisabled();
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isTokenExpired(String token) {
        final Date expiration = getAllClaimsFromToken(token).getExpiration();
        return expiration.before(new Date());
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public long accessTokenTtlSeconds() {
        return 15 * 60L;
    }

    private String buildToken(String subject, Map<String, Object> claims) {
        long JWT_EXPIRATION = accessTokenTtlSeconds() * 1000L;
        return Jwts.builder()
                .setSubject(subject)
                .addClaims(claims)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Role safeRole(Role role, Role fallback) {
        return role == null ? fallback : role;
    }
}
