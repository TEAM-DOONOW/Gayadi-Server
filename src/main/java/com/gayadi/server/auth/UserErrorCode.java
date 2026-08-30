package com.gayadi.server.auth;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum UserErrorCode implements ErrorCode {

    // User Lookup - 사용자 조회 및 활성 계정 확인
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
            "error.user.not-found", "사용자를 찾을 수 없습니다."),

    // Withdrawal - 회원 탈퇴 조건 및 소유 데이터 충돌
    USER_ACTIVE_OWNED_TRIP_EXISTS(HttpStatus.CONFLICT, "USER_ACTIVE_OWNED_TRIP_EXISTS",
            "error.user.active-owned-trip-exists", "진행 중이거나 준비 중인 소유 여행을 먼저 취소해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    UserErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
