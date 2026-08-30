package com.gayadi.server.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    // Request Validation - 요청값 및 요청 본문 검증
    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST",
            "error.common.invalid-request",
            "요청값이 올바르지 않습니다."),
    INVALID_PARAMETER_TYPE(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER_TYPE",
            "error.common.invalid-parameter-type",
            "요청 파라미터의 형식이 올바르지 않습니다."),
    MISSING_REQUIRED_PARAMETER(
            HttpStatus.BAD_REQUEST,
            "MISSING_REQUIRED_PARAMETER",
            "error.common.missing-required-parameter",
            "필수 요청 파라미터가 없습니다."),
    MALFORMED_REQUEST_BODY(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_REQUEST_BODY",
            "error.common.malformed-request-body",
            "요청 본문 형식이 올바르지 않습니다."),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "error.common.unsupported-media-type",
            "요청 본문은 application/json 형식으로 보내 주세요."),
    INVALID_DATE(HttpStatus.BAD_REQUEST, "INVALID_DATE",
            "error.common.invalid-date", "실제로 존재하는 {0}를 입력해 주세요."),
    INVALID_TIME(HttpStatus.BAD_REQUEST, "INVALID_TIME",
            "error.common.invalid-time", "실제로 존재하는 {0}을 입력해 주세요."),

    // HTTP Protocol - 경로, 메서드, 응답 형식 및 요청 크기
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "error.common.resource-not-found",
            "요청한 API 경로를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "error.common.method-not-allowed",
            "지원하지 않는 HTTP 메서드입니다."),
    NOT_ACCEPTABLE(
            HttpStatus.NOT_ACCEPTABLE,
            "NOT_ACCEPTABLE",
            "error.common.not-acceptable",
            "요청한 응답 형식을 제공할 수 없습니다."),
    REQUEST_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "REQUEST_TOO_LARGE",
            "error.common.request-too-large",
            "요청 데이터의 크기가 허용 범위를 초과했습니다."),
    ASYNC_REQUEST_TIMEOUT(
            HttpStatus.SERVICE_UNAVAILABLE,
            "ASYNC_REQUEST_TIMEOUT",
            "error.common.async-request-timeout",
            "요청 처리 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요."),

    // Data - 공통 데이터 제약 충돌
    DATA_CONFLICT(
            HttpStatus.CONFLICT,
            "DATA_CONFLICT",
            "error.common.data-conflict",
            "이미 사용 중인 값이거나 다른 데이터와 연결되어 있어 처리할 수 없습니다."),

    // Security - 인증 및 인가
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "error.common.unauthenticated",
            "로그인이 필요합니다."),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "error.common.access-denied",
            "요청을 처리할 권한이 없습니다."),

    // Server - 예상하지 못한 내부 오류
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "error.common.internal-server-error",
            "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    CommonErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
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

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
