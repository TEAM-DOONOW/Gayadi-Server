package com.gayadi.server.auth;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.config.security.RedisSecurityProperties;
import com.gayadi.server.config.security.RefreshTokenProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RefreshTokenService service = new RefreshTokenService(
            redis,
            new RefreshTokenProperties(true, Duration.ofDays(30), Duration.ofDays(90)),
            new RedisSecurityProperties(true, "gayadi:test:security", Duration.ofDays(90)));

    @Test
    void issuesOpaqueRefreshTokenWithoutUserInformation() {
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);

        RefreshTokenService.IssuedRefreshToken issued = service.issue(42L);

        assertThat(issued.token()).matches("[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}");
        assertThat(issued.expiresIn()).isPositive().isLessThanOrEqualTo(Duration.ofDays(30));
    }

    @SuppressWarnings("unchecked")
    @Test
    void returnsNewTokenAfterAtomicRotationSucceeds() {
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.multiGet(any(), anyList())).thenReturn(List.of(
                "session-id",
                "family-id",
                String.valueOf(Instant.now().plus(Duration.ofDays(90)).getEpochSecond())));
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(42L);

        String oldToken = validToken();
        RefreshTokenService.RotatedRefreshToken rotated = service.rotate(oldToken);

        assertThat(rotated.userId()).isEqualTo(42L);
        assertThat(rotated.token())
                .matches("[a-f0-9]{32}\\.[A-Za-z0-9_-]{43}")
                .isNotEqualTo(oldToken);
        assertThat(rotated.expiresIn()).isPositive().isLessThanOrEqualTo(Duration.ofDays(30));
    }

    @SuppressWarnings("unchecked")
    @Test
    void reportsReuseWhenAtomicRotationReturnsReuseResult() {
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashes);
        when(hashes.multiGet(any(), anyList())).thenReturn(List.of(
                "session-id",
                "family-id",
                String.valueOf(Instant.now().plus(Duration.ofDays(90)).getEpochSecond())));
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(-2L);

        assertThatThrownBy(() -> service.rotate(validToken()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED));
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectsRotationWhenSessionOrFamilyIsRevoked() {
        HashOperations<String, Object, Object> hashes = metadataHashes();
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(-1L);

        assertThatThrownBy(() -> service.rotate(validToken()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_REFRESH_SESSION_REVOKED));
    }

    @SuppressWarnings("unchecked")
    @Test
    void revokesCurrentSessionOnlyAfterTokenHashMatches() {
        HashOperations<String, Object, Object> hashes = metadataHashes();
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);

        service.revokeCurrent(validToken());
    }

    @SuppressWarnings("unchecked")
    @Test
    void rejectsLogoutWhenTokenHashDoesNotMatch() {
        HashOperations<String, Object, Object> hashes = metadataHashes();
        when(redis.opsForHash()).thenReturn(hashes);
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(0L);

        assertThatThrownBy(() -> service.revokeCurrent(validToken()))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID));
    }

    @Test
    void failsClosedWhenRedisCannotIssueToken() {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("unavailable"));

        assertThatThrownBy(() -> service.issue(42L))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_REFRESH_UNAVAILABLE));
    }

    @SuppressWarnings("unchecked")
    private HashOperations<String, Object, Object> metadataHashes() {
        HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
        when(hashes.multiGet(any(), anyList())).thenReturn(List.of(
                "session-id",
                "family-id",
                String.valueOf(Instant.now().plus(Duration.ofDays(90)).getEpochSecond())));
        return hashes;
    }

    private String validToken() {
        return "0123456789abcdef0123456789abcdef."
                + "abcdefghijklmnopqrstuvwxyzABCDEFGH123456789";
    }
}
