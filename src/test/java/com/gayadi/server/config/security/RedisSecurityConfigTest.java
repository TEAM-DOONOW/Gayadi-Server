package com.gayadi.server.config.security;

import com.gayadi.server.common.security.redis.SecurityRedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RedisSecurityConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withUserConfiguration(RedisSecurityConfig.class);

    @Test
    void doesNotCreateStoreWhenSecurityRedisIsDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.security.redis.enabled=false",
                        "app.security.redis.key-prefix=gayadi:test:security",
                        "app.security.redis.max-ttl=30d")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(SecurityRedisStore.class));
    }

    @Test
    void createsStoreOnlyWhenSecurityRedisIsEnabled() {
        contextRunner
                .withPropertyValues(
                        "app.security.redis.enabled=true",
                        "app.security.redis.key-prefix=gayadi:test:security",
                        "app.security.redis.max-ttl=30d")
                .run(context -> assertThat(context)
                        .hasSingleBean(SecurityRedisStore.class));
    }
}
