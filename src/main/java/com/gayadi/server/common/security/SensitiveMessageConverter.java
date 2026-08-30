package com.gayadi.server.common.security;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/** Logback이 최종 메시지를 출력하기 직전에 민감정보를 마스킹한다. */
public class SensitiveMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return SensitiveDataMasker.mask(event.getFormattedMessage());
    }
}
