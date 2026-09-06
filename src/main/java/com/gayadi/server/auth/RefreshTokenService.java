package com.gayadi.server.auth;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.config.security.RedisSecurityProperties;
import com.gayadi.server.config.security.RefreshTokenProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** 일회성 Refresh Token을 발급하고 Redis에서 원자적으로 회전·폐기합니다. */
@Service
@ConditionalOnProperty(prefix = "app.security.refresh-token", name = "enabled", havingValue = "true")
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();
    private static final DefaultRedisScript<Long> ISSUE_SCRIPT = script("issue-refresh-token.lua");
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = script("rotate-refresh-token.lua");
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = script("revoke-refresh-session.lua");

    private final StringRedisTemplate redis;
    private final RefreshTokenProperties refreshProperties;
    private final String keyPrefix;

    public RefreshTokenService(
            StringRedisTemplate redis,
            RefreshTokenProperties refreshProperties,
            RedisSecurityProperties redisProperties) {
        this.redis = redis;
        this.refreshProperties = refreshProperties;
        this.keyPrefix = redisProperties.keyPrefix();
    }

    /** 새 기기 로그인 세션과 최초 Refresh Token을 발급합니다. */
    public IssuedRefreshToken issue(long userId) {
        Instant now = Instant.now();
        String sessionId = id();
        String familyId = id();
        String tokenId = id();
        String secret = secret();
        Instant absoluteExpiry = now.plus(refreshProperties.absoluteLifetime());
        Duration ttl = effectiveTtl(now, absoluteExpiry);

        try {
            Long result = redis.execute(
                    ISSUE_SCRIPT,
                    List.of(tokenKey(tokenId)),
                    hash(secret),
                    String.valueOf(userId),
                    sessionId,
                    familyId,
                    String.valueOf(absoluteExpiry.getEpochSecond()),
                    String.valueOf(ttl.toSeconds()));
            if (result == null || result != 1) {
                throw unavailable();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }

        return new IssuedRefreshToken(tokenId + "." + secret, ttl);
    }

    /** 기존 Refresh Token을 한 번 소비하고 같은 세션의 새 토큰으로 교체합니다. */
    public RotatedRefreshToken rotate(String rawToken) {
        TokenParts old = parts(rawToken);
        String newTokenId = id();
        String newSecret = secret();
        TokenMetadata metadata = metadata(old.tokenId());
        Duration ttl = effectiveTtl(Instant.now(), metadata.absoluteExpiry());

        try {
            Long result = redis.execute(
                    ROTATE_SCRIPT,
                    List.of(
                            tokenKey(old.tokenId()),
                            familyRevokedKey(metadata.familyId()),
                            sessionRevokedKey(metadata.sessionId()),
                            tokenKey(newTokenId)),
                    hash(old.secret()),
                    hash(newSecret),
                    String.valueOf(ttl.toSeconds()));

            if (result == null || result == 0) {
                throw invalid();
            }
            if (result == -2) {
                throw new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_REUSED);
            }
            if (result < 0) {
                throw new BusinessException(AuthErrorCode.AUTH_REFRESH_SESSION_REVOKED);
            }
            return new RotatedRefreshToken(result, newTokenId + "." + newSecret, ttl);
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    /** 전달된 Refresh Token이 속한 현재 로그인 세션을 폐기합니다. */
    public void revokeCurrent(String rawToken) {
        TokenParts token = parts(rawToken);
        TokenMetadata metadata = metadata(token.tokenId());
        Duration ttl = effectiveTtl(Instant.now(), metadata.absoluteExpiry());

        try {
            Long result = redis.execute(
                    REVOKE_SCRIPT,
                    List.of(tokenKey(token.tokenId()), sessionRevokedKey(metadata.sessionId())),
                    hash(token.secret()),
                    String.valueOf(ttl.toSeconds()));
            if (result == null || result == 0) {
                throw invalid();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw unavailable();
        }
    }

    private TokenMetadata metadata(String tokenId) {
        try {
            List<Object> values = redis.opsForHash().multiGet(
                    tokenKey(tokenId), List.of("session", "family", "absoluteExpiry"));
            if (values.size() != 3 || values.stream().anyMatch(java.util.Objects::isNull)) {
                throw invalid();
            }
            return new TokenMetadata(
                    values.get(0).toString(),
                    values.get(1).toString(),
                    Instant.ofEpochSecond(Long.parseLong(values.get(2).toString())));
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw exception instanceof DataAccessException ? unavailable() : invalid();
        }
    }

    private Duration effectiveTtl(Instant now, Instant absoluteExpiry) {
        Duration remaining = Duration.between(now, absoluteExpiry);
        if (remaining.isZero() || remaining.isNegative()) {
            throw invalid();
        }
        return remaining.compareTo(refreshProperties.idleTimeout()) < 0
                ? remaining : refreshProperties.idleTimeout();
    }

    private TokenParts parts(String rawToken) {
        if (rawToken == null) {
            throw invalid();
        }
        String[] values = rawToken.trim().split("\\.", -1);
        if (values.length != 2 || !values[0].matches("[a-f0-9]{32}") || values[1].length() < 40) {
            throw invalid();
        }
        return new TokenParts(values[0], values[1]);
    }

    private String tokenKey(String tokenId) {
        return keyPrefix + ":auth:refresh:" + tokenId;
    }

    private String familyRevokedKey(String familyId) {
        return keyPrefix + ":auth:refresh-family-revoked:" + familyId;
    }

    private String sessionRevokedKey(String sessionId) {
        return keyPrefix + ":auth:session-revoked:" + sessionId;
    }

    private String id() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String secret() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return BASE64_URL.encodeToString(value);
    }

    private String hash(String value) {
        try {
            return BASE64_URL.encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Refresh Token 해시 알고리즘을 사용할 수 없습니다.", exception);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID);
    }

    private BusinessException unavailable() {
        return new BusinessException(AuthErrorCode.AUTH_REFRESH_UNAVAILABLE);
    }

    private static DefaultRedisScript<Long> script(String filename) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis-scripts/" + filename));
        script.setResultType(Long.class);
        return script;
    }

    public record IssuedRefreshToken(String token, Duration expiresIn) {
    }

    public record RotatedRefreshToken(long userId, String token, Duration expiresIn) {
    }

    private record TokenParts(String tokenId, String secret) {
    }

    private record TokenMetadata(String sessionId, String familyId, Instant absoluteExpiry) {
    }
}
