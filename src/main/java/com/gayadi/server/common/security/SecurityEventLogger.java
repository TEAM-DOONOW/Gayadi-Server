package com.gayadi.server.common.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 토큰·개인정보 없이 보안 판단 결과를 구조화된 형태로 기록합니다. */
@Component
public class SecurityEventLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityEventLogger.class);

    public void warn(String event, String endpoint, String outcome) {
        log.warn("security_event={} endpoint={} outcome={}", event, endpoint, outcome);
    }
}
