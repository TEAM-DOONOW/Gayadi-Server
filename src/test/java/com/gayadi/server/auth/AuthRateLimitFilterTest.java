package com.gayadi.server.auth;

import com.gayadi.server.common.security.ApiSecurityErrorWriter;
import com.gayadi.server.common.security.SecurityEventLogger;
import com.gayadi.server.common.exception.CommonErrorCode;
import com.gayadi.server.config.security.AuthRateLimitProperties;
import com.gayadi.server.config.security.RedisSecurityProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimitFilterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final ApiSecurityErrorWriter errorWriter = mock(ApiSecurityErrorWriter.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final SecurityEventLogger eventLogger = mock(SecurityEventLogger.class);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(
            redis,
            new AuthRateLimitProperties(true, 5, 10, 10, 30, 10, 20, 2, Duration.ofMinutes(1)),
            new RedisSecurityProperties(true, "gayadi:test:security", Duration.ofDays(90)),
            errorWriter,
            eventLogger);

    @Test
    void allowsAuthenticationRequestWithinLimit() throws Exception {
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(1L);
        MockHttpServletRequest request = request("POST", "/api/v1/auth/tokens");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(errorWriter, never()).write(any(), any(), any());
    }

    @Test
    void rejectsAuthenticationRequestOverLimit() throws Exception {
        when(redis.execute(any(), anyList(), any(Object[].class))).thenReturn(0L);
        MockHttpServletRequest request = request("POST", "/api/v1/auth/tokens");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(errorWriter).write(request, response, CommonErrorCode.REQUEST_RATE_LIMITED);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() throws Exception {
        when(redis.execute(any(), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("unavailable"));
        MockHttpServletRequest request = request("POST", "/api/v1/auth/token-refreshes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(errorWriter).write(request, response, CommonErrorCode.SECURITY_STORE_UNAVAILABLE);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void ignoresNonAuthenticationEndpoint() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/notices");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(redis, never()).execute(any(), anyList(), any(Object[].class));
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("192.0.2.10");
        return request;
    }
}
