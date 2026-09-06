package com.gayadi.server.config.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisSecurityPropertiesTest {

    @Test
    void normalizesValidConfiguration() {
        RedisSecurityProperties properties = new RedisSecurityProperties(
                true,
                " gayadi:test:security ",
                Duration.ofDays(30));

        assertThat(properties.keyPrefix()).isEqualTo("gayadi:test:security");
        assertThat(properties.maxTtl()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void rejectsUnsafeKeyPrefix() {
        assertThatThrownBy(() -> new RedisSecurityProperties(
                true,
                "gayadi test",
                Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaximumTtl() {
        assertThatThrownBy(() -> new RedisSecurityProperties(
                true,
                "gayadi:test:security",
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
