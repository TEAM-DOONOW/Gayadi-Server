package com.gayadi.server.common.security.redis;

import com.gayadi.server.config.security.RedisSecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityRedisStoreTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisSecurityProperties properties = new RedisSecurityProperties(
            true,
            "gayadi:test:security",
            Duration.ofDays(30));
    private final SecurityRedisStore store = new SecurityRedisStore(redis, properties);

    @Test
    void storesNamespacedValueWithMandatoryTtl() {
        when(redis.opsForValue()).thenReturn(values);

        store.put(
                SecurityRedisNamespace.SESSION,
                "session_123",
                "non-sensitive-state",
                Duration.ofHours(1));

        verify(values).set(
                "gayadi:test:security:auth:session:session_123",
                "non-sensitive-state",
                Duration.ofHours(1));
    }

    @Test
    void rejectsMissingOrExcessiveTtl() {
        assertThatThrownBy(() -> store.put(
                SecurityRedisNamespace.REFRESH_TOKEN,
                "token_123",
                "hashed-token-state",
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> store.put(
                SecurityRedisNamespace.REFRESH_TOKEN,
                "token_123",
                "hashed-token-state",
                Duration.ofDays(31)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsKeyDelimiterInjection() {
        assertThatThrownBy(() -> store.get(SecurityRedisNamespace.SESSION, "other:namespace"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
