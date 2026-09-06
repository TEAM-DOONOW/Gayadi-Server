package com.gayadi.server.common.security.redis;

import com.gayadi.server.config.security.RedisSecurityProperties;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/** TTL과 용도별 key namespace를 강제하여 Redis 보안 상태를 저장합니다. */
public class SecurityRedisStore {

    private static final Pattern KEY_ID_PATTERN = Pattern.compile("[a-zA-Z0-9_-]{1,128}");

    private final StringRedisTemplate redis;
    private final RedisSecurityProperties properties;

    public SecurityRedisStore(
            StringRedisTemplate redis,
            RedisSecurityProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 보안 상태를 지정한 TTL 동안만 저장합니다. */
    public void put(
            SecurityRedisNamespace namespace,
            String id,
            String value,
            Duration ttl) {
        validateTtl(ttl);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Redis 보안 데이터는 빈 값일 수 없습니다.");
        }

        redis.opsForValue().set(key(namespace, id), value, ttl);
    }

    /** 용도와 식별자가 일치하는 보안 상태를 조회합니다. */
    public Optional<String> get(SecurityRedisNamespace namespace, String id) {
        return Optional.ofNullable(redis.opsForValue().get(key(namespace, id)));
    }

    /** 용도와 식별자가 일치하는 보안 상태를 즉시 삭제합니다. */
    public boolean delete(SecurityRedisNamespace namespace, String id) {
        return Boolean.TRUE.equals(redis.delete(key(namespace, id)));
    }

    private String key(SecurityRedisNamespace namespace, String id) {
        if (namespace == null) {
            throw new IllegalArgumentException("Redis 보안 key namespace가 필요합니다.");
        }
        if (id == null || !KEY_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Redis 보안 key 식별자가 올바르지 않습니다.");
        }
        return properties.keyPrefix() + ":" + namespace.keySegment() + ":" + id;
    }

    private void validateTtl(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative() || ttl.compareTo(properties.maxTtl()) > 0) {
            throw new IllegalArgumentException("Redis 보안 데이터 TTL이 허용 범위를 벗어났습니다.");
        }
    }
}
