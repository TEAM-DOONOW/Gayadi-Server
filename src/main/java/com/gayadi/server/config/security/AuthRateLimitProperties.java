package com.gayadi.server.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 인증 API별 요청 한도와 고정 구간 길이를 정의합니다. */
@ConfigurationProperties(prefix = "app.security.auth-rate-limit")
public record AuthRateLimitProperties(
        boolean enabled,
        int registrationLimit,
        int loginLimit,
        int googleLoginLimit,
        int refreshLimit,
        int invitationLimit,
        int aiLimit,
        int adminLimit,
        Duration window
) {

    public AuthRateLimitProperties {
        registrationLimit = positiveOrDefault(registrationLimit, 5);
        loginLimit = positiveOrDefault(loginLimit, 10);
        googleLoginLimit = positiveOrDefault(googleLoginLimit, 10);
        refreshLimit = positiveOrDefault(refreshLimit, 30);
        invitationLimit = positiveOrDefault(invitationLimit, 10);
        aiLimit = positiveOrDefault(aiLimit, 20);
        adminLimit = positiveOrDefault(adminLimit, 2);
        window = window == null ? Duration.ofMinutes(1) : window;

        if (window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("인증 Rate Limit 구간은 0보다 커야 합니다.");
        }
    }

    private static int positiveOrDefault(int value, int defaultValue) {
        return value > 0 ? value : defaultValue;
    }
}
