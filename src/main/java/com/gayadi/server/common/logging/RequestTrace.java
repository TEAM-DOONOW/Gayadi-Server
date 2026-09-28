package com.gayadi.server.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

/** HTTP 요청 로그와 오류 응답이 공유하는 추적 식별자를 관리합니다. */
public final class RequestTrace {

    public static final String MDC_KEY = "traceId";
    public static final String RESPONSE_HEADER = "X-Trace-Id";

    private RequestTrace() {
    }

    public static String currentOrCreate() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank()
                ? UUID.randomUUID().toString()
                : traceId;
    }
}
