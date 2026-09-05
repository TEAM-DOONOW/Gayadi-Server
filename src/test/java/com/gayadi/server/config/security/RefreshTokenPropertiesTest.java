package com.gayadi.server.config.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenPropertiesTest {

    @Test
    void appliesAndroidSessionDefaults() {
        RefreshTokenProperties properties = new RefreshTokenProperties(true, null, null);

        assertThat(properties.idleTimeout()).isEqualTo(Duration.ofDays(30));
        assertThat(properties.absoluteLifetime()).isEqualTo(Duration.ofDays(90));
    }

    @Test
    void rejectsIdleTimeoutLongerThanAbsoluteLifetime() {
        assertThatThrownBy(() -> new RefreshTokenProperties(
                true,
                Duration.ofDays(31),
                Duration.ofDays(30)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
