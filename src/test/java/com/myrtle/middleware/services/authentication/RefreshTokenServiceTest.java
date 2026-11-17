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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CorporateClientRepository corporateClientRepository;

    @Mock
    private JwtUtils jwtUtils;

    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(
                refreshTokenRepository,
                userRepository,
                corporateClientRepository,
                jwtUtils
        );
    }

    @Test
    void issueStoresOnlyAHashAndReturnsTheRawToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String raw = refreshTokenService.issueForUser(user());

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());
        RefreshToken stored = captor.getValue();
        assertThat(raw).isNotBlank();
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getSubjectType()).isEqualTo(RefreshSubjectType.USER);
        assertThat(stored.getSubjectId()).isEqualTo("user-1");
        assertThat(stored.isRevoked()).isFalse();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now().plus(29, ChronoUnit.DAYS));
    }

    @Test
    void rotateReplacesTheRefreshTokenAndIssuesANewAccessToken() {
        AtomicReference<RefreshToken> issued = new AtomicReference<>();
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0);
            issued.compareAndSet(null, token);
            return token;
        });
        String raw = refreshTokenService.issueForUser(user());
        issued.get().setId("refresh-1");

        when(refreshTokenRepository.findByTokenHash(issued.get().getTokenHash())).thenReturn(Optional.of(issued.get()));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user()));
        when(refreshTokenRepository.revokeIfActive(any(), any())).thenReturn(1);
        when(jwtUtils.generateToken(any(User.class))).thenReturn("new-access");

        IssuedTokens tokens = refreshTokenService.rotate(raw);

        assertThat(tokens.accessToken()).isEqualTo("new-access");
        assertThat(tokens.refreshToken()).isNotBlank().isNotEqualTo(raw);
        verify(refreshTokenRepository).revokeIfActive(any(), any());
    }

    @Test
    void rotateRejectsAnUnknownExpiredOrReusedToken() {
        assertThatThrownBy(() -> refreshTokenService.rotate("   "))
                .isInstanceOf(InvalidRefreshTokenException.class);

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> refreshTokenService.rotate("missing"))
                .isInstanceOf(InvalidRefreshTokenException.class);

        RefreshToken expired = activeToken();
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> refreshTokenService.rotate("expired"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).revokeActiveForSubject(any(), any(), any());

        RefreshToken reused = activeToken();
        reused.setRevoked(true);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(reused));
        assertThatThrownBy(() -> refreshTokenService.rotate("reused"))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeActiveForSubject(eq(RefreshSubjectType.USER), eq("user-1"), any());
    }

    @Test
    void rotateRejectsADisabledUserAndRevokesTheirSessions() {
        RefreshToken stored = activeToken();
        User disabled = user();
        disabled.setDisabled(true);
        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(userRepository.findById("user-1")).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> refreshTokenService.rotate("raw"))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessageContaining("Account disabled");
        verify(refreshTokenRepository).revokeActiveForSubject(eq(RefreshSubjectType.USER), eq("user-1"), any());
        verify(jwtUtils, never()).generateToken(any(User.class));
    }

    @Test
    void rotateIssuesACorporateAccessToken() {
        RefreshToken stored = activeToken();
        stored.setSubjectType(RefreshSubjectType.CORPORATE_ACCOUNT);
        stored.setSubjectId("corp-1");
        CorporateClient corporateClient = new CorporateClient();
        corporateClient.setId("corp-1");
        corporateClient.setEmail("corp@example.com");
        corporateClient.setRole(Role.CORPORATE_ACCOUNT);
        corporateClient.setGrantLoginAccess(true);

        when(refreshTokenRepository.findByTokenHash(any())).thenReturn(Optional.of(stored));
        when(corporateClientRepository.findById("corp-1")).thenReturn(Optional.of(corporateClient));
        when(refreshTokenRepository.revokeIfActive(any(), any())).thenReturn(1);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtUtils.generateToken(any(CorporateClient.class))).thenReturn("corp-access");

        IssuedTokens tokens = refreshTokenService.rotate("raw");

        assertThat(tokens.accessToken()).isEqualTo("corp-access");
        assertThat(tokens.refreshToken()).isNotBlank();
    }

    private User user() {
        User user = new User();
        user.setId("user-1");
        user.setEmailAddress("user@example.com");
        user.setRole(Role.USER);
        return user;
    }

    private RefreshToken activeToken() {
        RefreshToken token = new RefreshToken();
        token.setId("refresh-1");
        token.setTokenHash("a".repeat(64));
        token.setSubjectType(RefreshSubjectType.USER);
        token.setSubjectId("user-1");
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.DAYS));
        token.setRevoked(false);
        return token;
    }

    @Test
    void purgeDeletesStaleTokensInBatchesUntilTheTableIsDrained() {
        ReflectionTestUtils.setField(refreshTokenService, "purgeBatchSize", 2);
        when(refreshTokenRepository.deletePurgeableBatch(any(Instant.class), eq(2))).thenReturn(2, 2, 1);

        refreshTokenService.purgeStaleTokens();

        verify(refreshTokenRepository, times(3)).deletePurgeableBatch(any(Instant.class), eq(2));
    }

    @Test
    void purgeKeepsRowsInsideTheGraceWindow() {
        ReflectionTestUtils.setField(refreshTokenService, "purgeGraceDays", 7L);
        when(refreshTokenRepository.deletePurgeableBatch(any(Instant.class), anyInt())).thenReturn(0);
        Instant startedAt = Instant.now();

        refreshTokenService.purgeStaleTokens();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(refreshTokenRepository).deletePurgeableBatch(cutoff.capture(), anyInt());
        assertThat(cutoff.getValue()).isAfterOrEqualTo(startedAt.minus(7, ChronoUnit.DAYS));
        assertThat(cutoff.getValue()).isBefore(startedAt.minus(6, ChronoUnit.DAYS));
    }

    @Test
    void purgeStopsAtThePerRunCap() {
        ReflectionTestUtils.setField(refreshTokenService, "purgeBatchSize", 2);
        ReflectionTestUtils.setField(refreshTokenService, "purgeMaxRowsPerRun", 4);
        when(refreshTokenRepository.deletePurgeableBatch(any(Instant.class), eq(2))).thenReturn(2);

        refreshTokenService.purgeStaleTokens();

        verify(refreshTokenRepository, times(2)).deletePurgeableBatch(any(Instant.class), eq(2));
    }

    @Test
    void purgeDoesNothingWhenDisabled() {
        ReflectionTestUtils.setField(refreshTokenService, "purgeEnabled", false);

        refreshTokenService.purgeStaleTokens();

        verify(refreshTokenRepository, never()).deletePurgeableBatch(any(Instant.class), anyInt());
    }
}
