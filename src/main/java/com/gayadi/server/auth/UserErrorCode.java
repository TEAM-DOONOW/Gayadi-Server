package com.gayadi.server.auth;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 인증과 사용자 계정 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum UserErrorCode implements ErrorCode {

    // User Lookup - 사용자 조회 및 활성 계정 확인
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND",
            "error.user.not-found"),

    // Withdrawal - 회원 탈퇴 조건 및 소유 데이터 충돌
    USER_ACTIVE_OWNED_TRIP_EXISTS(HttpStatus.CONFLICT, "USER_ACTIVE_OWNED_TRIP_EXISTS",
            "error.user.active-owned-trip-exists");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    UserErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override

    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
