package com.gayadi.server.common.security.redis;

/** Redis 보안 key를 세션·Refresh Token·남용 제한 용도로 분리합니다. */
public enum SecurityRedisNamespace {

    SESSION("auth:session"),
    REFRESH_TOKEN("auth:refresh"),
    RATE_LIMIT("auth:rate-limit");

    private final String keySegment;

    SecurityRedisNamespace(String keySegment) {
        this.keySegment = keySegment;
    }

    public String keySegment() {
        return keySegment;
    }
}
