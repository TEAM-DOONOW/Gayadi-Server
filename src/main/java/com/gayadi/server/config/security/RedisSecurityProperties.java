package com.gayadi.server.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Redis에 저장할 보안 상태의 key 범위와 TTL 상한을 정의합니다. */
@ConfigurationProperties(prefix = "app.security.redis")
public record RedisSecurityProperties(
        boolean enabled,
        String keyPrefix,
        Duration maxTtl
) {

    private static final Duration DEFAULT_MAX_TTL = Duration.ofDays(90);

    public RedisSecurityProperties {
        keyPrefix = normalizePrefix(keyPrefix);
        maxTtl = maxTtl == null ? DEFAULT_MAX_TTL : maxTtl;

        if (maxTtl.isZero() || maxTtl.isNegative()) {
            throw new IllegalArgumentException("Redis 보안 데이터의 최대 TTL은 0보다 커야 합니다.");
        }
    }

    private static String normalizePrefix(String value) {
        String prefix = value == null ? "" : value.trim();
        if (prefix.isEmpty() || prefix.startsWith(":") || prefix.endsWith(":")) {
            throw new IllegalArgumentException("Redis 보안 key prefix가 올바르지 않습니다.");
        }
        if (!prefix.matches("[a-zA-Z0-9:_-]+")) {
            throw new IllegalArgumentException("Redis 보안 key prefix에 허용되지 않은 문자가 있습니다.");
        }
        return prefix;
    }
}
