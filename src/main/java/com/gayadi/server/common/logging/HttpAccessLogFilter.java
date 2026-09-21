package com.gayadi.server.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 요청 본문이나 인증정보를 제외한 HTTP 접근 로그를 남깁니다. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(HttpAccessLogFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String previousTraceId = MDC.get(RequestTrace.MDC_KEY);
        String traceId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();

        MDC.put(RequestTrace.MDC_KEY, traceId);
        response.setHeader(RequestTrace.RESPONSE_HEADER, traceId);
        boolean completed = false;
        try {
            filterChain.doFilter(request, response);
            completed = true;
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            int status = completed || response.getStatus() >= 400
                    ? response.getStatus()
                    : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            log.info("http_request method={} path={} status={} durationMs={}",
                    request.getMethod(), request.getRequestURI(), status, durationMs);
            restoreTraceId(previousTraceId);
        }
    }

    private void restoreTraceId(String previousTraceId) {
        if (previousTraceId == null) {
            MDC.remove(RequestTrace.MDC_KEY);
            return;
        }
        MDC.put(RequestTrace.MDC_KEY, previousTraceId);
    }
}
