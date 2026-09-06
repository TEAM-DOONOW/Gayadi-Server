package com.gayadi.server.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Refresh Token의 활성화 여부와 만료 정책을 정의합니다. */
@ConfigurationProperties(prefix = "app.security.refresh-token")
public record RefreshTokenProperties(
        boolean enabled,
        Duration idleTimeout,
        Duration absoluteLifetime
) {

    private static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofDays(30);
    private static final Duration DEFAULT_ABSOLUTE_LIFETIME = Duration.ofDays(90);

    public RefreshTokenProperties {
        idleTimeout = idleTimeout == null ? DEFAULT_IDLE_TIMEOUT : idleTimeout;
        absoluteLifetime = absoluteLifetime == null ? DEFAULT_ABSOLUTE_LIFETIME : absoluteLifetime;

        if (idleTimeout.isZero() || idleTimeout.isNegative()
                || absoluteLifetime.isZero() || absoluteLifetime.isNegative()) {
            throw new IllegalArgumentException("Refresh 세션 만료 시간은 0보다 커야 합니다.");
        }
        if (idleTimeout.compareTo(absoluteLifetime) > 0) {
            throw new IllegalArgumentException("Refresh idle timeout은 세션 최대 수명보다 길 수 없습니다.");
        }
    }
}
