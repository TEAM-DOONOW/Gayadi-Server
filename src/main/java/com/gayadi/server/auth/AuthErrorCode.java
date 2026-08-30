package com.gayadi.server.auth;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {

    // Registration - 회원가입 및 계정 식별자 중복
    AUTH_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_EMAIL_ALREADY_REGISTERED",
            "error.auth.email-already-registered", "이미 가입된 이메일입니다."),

    // Login - 로그인 자격 증명, 계정 상태 및 시도 제한
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS",
            "error.auth.invalid-credentials", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_ACCOUNT_UNAVAILABLE(HttpStatus.FORBIDDEN, "AUTH_ACCOUNT_UNAVAILABLE",
            "error.auth.account-unavailable", "사용할 수 없는 계정입니다."),
    AUTH_LOGIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_RATE_LIMITED",
            "error.auth.login-rate-limited", "로그인 시도가 많아 잠시 잠겼습니다. 15분 뒤 다시 시도해 주세요."),

    // Token - 로그인 토큰 형식, 서명 및 만료
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_INVALID",
            "error.auth.token-invalid", "유효하지 않은 토큰입니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_EXPIRED",
            "error.auth.token-expired", "로그인이 만료되었습니다. 다시 로그인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    AuthErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
