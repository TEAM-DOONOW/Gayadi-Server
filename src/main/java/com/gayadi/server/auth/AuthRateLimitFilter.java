package com.gayadi.server.auth;

import com.gayadi.server.common.security.ApiSecurityErrorWriter;
import com.gayadi.server.common.security.SecurityEventLogger;
import com.gayadi.server.common.exception.CommonErrorCode;
import com.gayadi.server.config.security.AuthRateLimitProperties;
import com.gayadi.server.config.security.RedisSecurityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 공개 인증 API의 무차별 요청을 Redis 기반 요청 한도로 차단합니다. */
@Component
@ConditionalOnProperty(prefix = "app.security.auth-rate-limit", name = "enabled", havingValue = "true")
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final DefaultRedisScript<Long> SCRIPT = script();

    private final StringRedisTemplate redis;
    private final AuthRateLimitProperties properties;
    private final ApiSecurityErrorWriter errorWriter;
    private final SecurityEventLogger eventLogger;
    private final String keyPrefix;

    public AuthRateLimitFilter(
            StringRedisTemplate redis,
            AuthRateLimitProperties properties,
            RedisSecurityProperties redisProperties,
            ApiSecurityErrorWriter errorWriter,
            SecurityEventLogger eventLogger) {
        this.redis = redis;
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.eventLogger = eventLogger;
        this.keyPrefix = redisProperties.keyPrefix();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Policy policy = policy(request);
        if (policy == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Long allowed = redis.execute(
                    SCRIPT,
                    List.of(key(policy.bucket(), request.getRemoteAddr())),
                    String.valueOf(policy.limit()),
                    String.valueOf(properties.window().toSeconds()));

            if (allowed == null || allowed == 0) {
                eventLogger.warn("AUTH_RATE_LIMIT", request.getRequestURI(), "DENIED");
                errorWriter.write(request, response, CommonErrorCode.REQUEST_RATE_LIMITED);
                return;
            }
        } catch (DataAccessException exception) {
            eventLogger.warn("AUTH_RATE_LIMIT_STORE", request.getRequestURI(), "UNAVAILABLE");
            errorWriter.write(request, response, CommonErrorCode.SECURITY_STORE_UNAVAILABLE);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Policy policy(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (uri.matches("/api/v1/trips/[0-9]+/invitations")) {
            return new Policy("invitation-issue", properties.invitationLimit());
        }
        return switch (uri) {
            case "/api/v1/auth/registrations" -> new Policy("registration", properties.registrationLimit());
            case "/api/v1/auth/tokens" -> new Policy("login", properties.loginLimit());
            case "/api/v1/auth/google-tokens" -> new Policy("google", properties.googleLoginLimit());
            case "/api/v1/auth/token-refreshes" -> new Policy("refresh", properties.refreshLimit());
            case "/api/v1/trip-memberships" -> new Policy("invitation-join", properties.invitationLimit());
            case "/api/v1/recommendations/places",
                    "/api/v1/recommendations/situations" -> new Policy("ai", properties.aiLimit());
            case "/api/v1/admin/place-embeddings" -> new Policy("admin", properties.adminLimit());
            default -> null;
        };
    }

    private String key(String bucket, String remoteAddress) {
        return keyPrefix + ":auth:rate-limit:" + bucket + ":" + remoteAddress;
    }

    private static DefaultRedisScript<Long> script() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis-scripts/auth-rate-limit.lua"));
        script.setResultType(Long.class);
        return script;
    }

    private record Policy(String bucket, int limit) {
    }
}
