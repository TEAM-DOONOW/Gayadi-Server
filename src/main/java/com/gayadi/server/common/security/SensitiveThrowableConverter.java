package com.gayadi.server.common.security;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** 예외 메시지와 스택 트레이스에 포함된 민감정보도 출력 전에 마스킹한다. */
public class SensitiveThrowableConverter extends ThrowableProxyConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(super.convert(event));
    }
}
