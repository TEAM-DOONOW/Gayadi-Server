package com.gayadi.server.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Redis 보안 필터를 활성화한 실제 SecurityFilterChain 구성이 시작되는지 검증합니다. */
@SpringBootTest(properties = {
        "app.security.redis.enabled=true",
        "app.security.auth-rate-limit.enabled=true"
})
class RateLimitSecurityContextTest {

    @Test
    void startsSecurityFilterChainWithRateLimitEnabled() {
    }
}
