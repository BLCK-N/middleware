package com.myrtle.middleware.services.authentication;

import com.myrtle.middleware.dtos.response.IssuedTokens;
import com.myrtle.middleware.enums.RefreshSubjectType;
import com.myrtle.middleware.enums.Role;
import com.myrtle.middleware.exceptions.InvalidRefreshTokenException;
import com.myrtle.middleware.model.CorporateClient;
import com.myrtle.middleware.model.RefreshToken;
import com.myrtle.middleware.model.User;
import com.myrtle.middleware.repository.CorporateClientRepository;
import com.myrtle.middleware.repository.RefreshTokenRepository;
import com.myrtle.middleware.repository.UserRepository;
import com.myrtle.middleware.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    static final String INVALID_REFRESH_TOKEN = "Refresh token is invalid or has expired.";

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final CorporateClientRepository corporateClientRepository;
    private final JwtUtils jwtUtils;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${jwt.refresh-expiration-days:30}")
    private long refreshExpirationDays = 30;

    @Value("${app.refresh-token.purge.enabled:true}")
    private boolean purgeEnabled = true;

    @Value("${app.refresh-token.purge.grace-days:7}")
    private long purgeGraceDays = 7;

    @Value("${app.refresh-token.purge.batch-size:500}")
    private int purgeBatchSize = 500;

    @Value("${app.refresh-token.purge.max-rows-per-run:50000}")
    private int purgeMaxRowsPerRun = 50_000;

    public String issueForUser(User user) {
        if (user == null || !StringUtils.hasText(user.getId())) {
            throw new IllegalStateException("Cannot issue a refresh token for a user without an id");
        }
        return issue(RefreshSubjectType.USER, user.getId());
    }

    public String issueForCorporateClient(CorporateClient corporateClient) {
        if (corporateClient == null || !StringUtils.hasText(corporateClient.getId())) {
            throw new IllegalStateException("Cannot issue a refresh token for a corporate account without an id");
        }
        return issue(RefreshSubjectType.CORPORATE_ACCOUNT, corporateClient.getId());
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public IssuedTokens rotate(String rawRefreshToken) {
        RefreshToken stored = requireActiveToken(rawRefreshToken);
        assertSubjectCanRefresh(stored);

        int revoked = refreshTokenRepository.revokeIfActive(stored.getId(), Instant.now());
        if (revoked == 0) {
            revokeFamily(stored);
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN);
        }

        String accessToken = mintAccessToken(stored);
        String refreshToken = issue(stored.getSubjectType(), stored.getSubjectId());
        log.info("Refresh token rotated | subjectType={} | subjectId={}", stored.getSubjectType(), stored.getSubjectId());
        return new IssuedTokens(accessToken, refreshToken);
    }

    @Transactional
    public void revokeForUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }
        int revoked = refreshTokenRepository.revokeActiveForSubject(RefreshSubjectType.USER, userId, Instant.now());
        if (revoked > 0) {
            log.info("Refresh tokens revoked | subjectType=USER | subjectId={} | count={}", userId, revoked);
        }
    }

    @Scheduled(cron = "${app.refresh-token.purge.cron:0 30 3 * * *}", zone = "${app.refresh-token.purge.zone:Africa/Lagos}")
    public void purgeStaleTokens() {
        if (!purgeEnabled) {
            return;
        }

        Instant cutoff = Instant.now().minus(Math.max(purgeGraceDays, 1L), ChronoUnit.DAYS);
        int batchSize = Math.max(purgeBatchSize, 1);
        int maxRows = Math.max(purgeMaxRowsPerRun, batchSize);
        int deleted = 0;
        int removed;

        do {
            removed = refreshTokenRepository.deletePurgeableBatch(cutoff, batchSize);
            deleted += removed;
        } while (removed == batchSize && deleted < maxRows);

        if (deleted > 0) {
            log.info("Refresh token purge finished | cutoff={} | deleted={}", cutoff, deleted);
        }
        if (removed == batchSize && deleted >= maxRows) {
            log.warn("Refresh token purge stopped at the per-run cap | deleted={} | cap={}", deleted, maxRows);
        }
    }

    private String issue(RefreshSubjectType subjectType, String subjectId) {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setSubjectType(subjectType);
        refreshToken.setSubjectId(subjectId);
        refreshToken.setCreatedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plus(refreshExpirationDays, ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        refreshTokenRepository.save(refreshToken);

        log.info("Refresh token issued | subjectType={} | subjectId={}", subjectType, subjectId);
        return rawToken;
    }

    private RefreshToken requireActiveToken(String rawRefreshToken) {
        if (!StringUtils.hasText(rawRefreshToken)) {
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN);
        }

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(rawRefreshToken.trim()))
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN));

        if (stored.isRevoked()) {
            revokeFamily(stored);
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN);
        }
        if (stored.getExpiresAt() == null || !stored.getExpiresAt().isAfter(Instant.now())) {
            throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN);
        }
        return stored;
    }

    private void assertSubjectCanRefresh(RefreshToken stored) {
        if (stored.getSubjectType() == RefreshSubjectType.CORPORATE_ACCOUNT) {
            CorporateClient corporateClient = corporateClientRepository.findById(stored.getSubjectId())
                    .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN));
            if (Boolean.FALSE.equals(corporateClient.getGrantLoginAccess())) {
                revokeFamily(stored);
                throw new InvalidRefreshTokenException("Login access is disabled for this corporate account.");
            }
            if (corporateClient.getRole() != null && corporateClient.getRole() != Role.CORPORATE_ACCOUNT) {
                revokeFamily(stored);
                throw new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN);
            }
            return;
        }

        User user = userRepository.findById(stored.getSubjectId())
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN));
        if (user.isDeleted()) {
            revokeFamily(stored);
            throw new InvalidRefreshTokenException("Account has been deleted");
        }
        if (user.isDisabled()) {
            revokeFamily(stored);
            throw new InvalidRefreshTokenException("Account disabled. Please reach out to admin at wecare@myrtleng.com");
        }
    }

    private String mintAccessToken(RefreshToken stored) {
        if (stored.getSubjectType() == RefreshSubjectType.CORPORATE_ACCOUNT) {
            CorporateClient corporateClient = corporateClientRepository.findById(stored.getSubjectId())
                    .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN));
            return jwtUtils.generateToken(corporateClient);
        }

        User user = userRepository.findById(stored.getSubjectId())
                .orElseThrow(() -> new InvalidRefreshTokenException(INVALID_REFRESH_TOKEN));
        return jwtUtils.generateToken(user);
    }

    private void revokeFamily(RefreshToken stored) {
        if (stored.getSubjectType() == null || !StringUtils.hasText(stored.getSubjectId())) {
            return;
        }
        int revoked = refreshTokenRepository.revokeActiveForSubject(
                stored.getSubjectType(), stored.getSubjectId(), Instant.now());
        if (revoked > 0) {
            log.warn("Refresh token reuse revoked active sessions | subjectType={} | subjectId={} | count={}",
                    stored.getSubjectType(), stored.getSubjectId(), revoked);
        }
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
