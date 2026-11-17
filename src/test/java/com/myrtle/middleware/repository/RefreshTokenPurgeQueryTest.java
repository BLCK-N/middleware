package com.myrtle.middleware.repository;

import com.myrtle.middleware.enums.RefreshSubjectType;
import com.myrtle.middleware.model.RefreshToken;
import com.myrtle.middleware.model.converter.EncryptedStringConverter;
import com.myrtle.middleware.utils.FieldEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * deletePurgeableBatch is a native query, so Hibernate never parses it at startup. Without this
 * the statement would first run when the purge cron fires.
 */
@DataJpaTest
@Import({FieldEncryptor.class, EncryptedStringConverter.class})
@EntityScan(basePackages = "com.myrtle.middleware.model")
@TestPropertySource(properties = {
        "app.encryption.key=Zml4bWUtbG9jYWwtb25seS1rZXktbm90LWZvci1wcm9kIQ==",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=dGVzdC1zZWNyZXQtdGhhdC1pcy1sb25nLWVub3VnaC1mb3ItaG1hYw==",
        "DB_URL=unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "ENVIRONMENT=test"
})
class RefreshTokenPurgeQueryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshToken save(String hash, Instant expiresAt, boolean revoked, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(hash);
        token.setSubjectType(RefreshSubjectType.USER);
        token.setSubjectId("user-1");
        token.setCreatedAt(Instant.now().minus(60, ChronoUnit.DAYS));
        token.setExpiresAt(expiresAt);
        token.setRevoked(revoked);
        token.setRevokedAt(revokedAt);
        return refreshTokenRepository.saveAndFlush(token);
    }

    private List<String> remainingHashes() {
        return refreshTokenRepository.findAll().stream().map(RefreshToken::getTokenHash).sorted().toList();
    }

    @Test
    void deletesOnlyRowsOlderThanTheCutoff() {
        Instant now = Instant.now();
        save("long-expired", now.minus(30, ChronoUnit.DAYS), false, null);
        save("just-expired", now.minus(1, ChronoUnit.HOURS), false, null);
        save("still-active", now.plus(30, ChronoUnit.DAYS), false, null);

        int deleted = refreshTokenRepository.deletePurgeableBatch(now.minus(7, ChronoUnit.DAYS), 500);

        assertThat(deleted).isEqualTo(1);
        assertThat(remainingHashes()).containsExactly("just-expired", "still-active");
    }

    @Test
    void deletesRevokedRowsPastTheCutoffButKeepsRecentlyRevokedOnes() {
        Instant now = Instant.now();
        save("revoked-old", now.plus(20, ChronoUnit.DAYS), true, now.minus(30, ChronoUnit.DAYS));
        save("revoked-recent", now.plus(20, ChronoUnit.DAYS), true, now.minus(1, ChronoUnit.HOURS));

        int deleted = refreshTokenRepository.deletePurgeableBatch(now.minus(7, ChronoUnit.DAYS), 500);

        assertThat(deleted).isEqualTo(1);
        assertThat(remainingHashes()).containsExactly("revoked-recent");
    }

    @Test
    void honoursTheBatchLimit() {
        Instant now = Instant.now();
        for (int i = 0; i < 5; i++) {
            save("expired-" + i, now.minus(30, ChronoUnit.DAYS), false, null);
        }

        int deleted = refreshTokenRepository.deletePurgeableBatch(now.minus(7, ChronoUnit.DAYS), 2);

        assertThat(deleted).isEqualTo(2);
        assertThat(remainingHashes()).hasSize(3);
    }

    @Test
    void keepsRevokedRowsThatHaveNoRevokedAtUntilTheyExpire() {
        Instant now = Instant.now();
        save("revoked-no-timestamp", now.plus(20, ChronoUnit.DAYS), true, null);

        int deleted = refreshTokenRepository.deletePurgeableBatch(now.minus(7, ChronoUnit.DAYS), 500);

        assertThat(deleted).isZero();
        assertThat(remainingHashes()).containsExactly("revoked-no-timestamp");
    }
}
