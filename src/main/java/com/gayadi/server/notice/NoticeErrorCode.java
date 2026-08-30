package com.gayadi.server.notice;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NoticeErrorCode implements ErrorCode {

    // Lookup - 공지 식별자 및 조회
    NOTICE_ID_INVALID(HttpStatus.BAD_REQUEST, "NOTICE_ID_INVALID",
            "error.notice.id-invalid", "공지 식별자가 올바르지 않습니다."),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND",
            "error.notice.not-found", "공지를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    NoticeErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
    @Override public String defaultMessage() { return defaultMessage; }
}
