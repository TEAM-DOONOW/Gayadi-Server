package com.gayadi.server.common.response;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** ErrorCode의 메시지 키를 요청 언어에 맞는 안전한 문구로 변환한다. */
@Component
public class ApiMessageResolver {

    private final MessageSource messageSource;

    public ApiMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String resolve(ErrorCode errorCode, Locale locale, Object... arguments) {
        Locale targetLocale = locale == null ? LocaleContextHolder.getLocale() : locale;
        return messageSource.getMessage(
                errorCode.messageKey(),
                arguments,
                targetLocale);
    }
}
