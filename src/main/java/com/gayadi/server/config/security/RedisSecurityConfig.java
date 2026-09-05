package com.gayadi.server.config.security;

import com.gayadi.server.common.security.redis.SecurityRedisStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Refresh 세션과 인증 rate limit에 사용할 Redis 보안 저장소를 구성합니다. */
@Configuration
@EnableConfigurationProperties({RedisSecurityProperties.class, RefreshTokenProperties.class})
public class RedisSecurityConfig {

    @Bean
    @ConditionalOnProperty(prefix = "app.security.redis", name = "enabled", havingValue = "true")
    SecurityRedisStore securityRedisStore(
            StringRedisTemplate redis,
            RedisSecurityProperties properties) {
        return new SecurityRedisStore(redis, properties);
    }
}
