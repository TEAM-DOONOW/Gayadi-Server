package com.gayadi.server.auth;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 인증과 사용자 계정 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum AuthErrorCode implements ErrorCode {

    // Registration - 회원가입 및 계정 식별자 중복
    AUTH_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_REGISTERED",
            "error.auth.email-already-registered"),

    // Login - 로그인 자격 증명, 계정 상태 및 시도 제한
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
            "error.auth.invalid-credentials"),
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_UNAVAILABLE",
            "error.auth.account-unavailable"),
    AUTH_LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_RATE_LIMITED",
            "error.auth.login-rate-limited"),

    // Token - 로그인 토큰 형식, 서명 및 만료
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID",
            "error.auth.token-invalid"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED",
            "error.auth.token-expired");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    AuthErrorCode(HttpStatus status, String code, String messageKey) {
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
