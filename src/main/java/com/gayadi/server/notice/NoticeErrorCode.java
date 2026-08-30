package com.gayadi.server.notice;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NoticeErrorCode implements ErrorCode {

    // Lookup - 공지 식별자 및 조회
    NOTICE_ID_INVALID(HttpStatus.BAD_REQUEST, "NOTICE_ID_INVALID",
            "error.notice.id-invalid"),
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTICE_NOT_FOUND",
            "error.notice.not-found");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    NoticeErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
