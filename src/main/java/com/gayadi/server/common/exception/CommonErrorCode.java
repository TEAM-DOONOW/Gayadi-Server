package com.gayadi.server.common.exception;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    // Request Validation - 요청값 및 요청 본문 검증
    INVALID_REQUEST(
            HttpStatus.BAD_REQUEST,
            "INVALID_REQUEST",
            "error.common.invalid-request"),
    INVALID_PARAMETER_TYPE(
            HttpStatus.BAD_REQUEST,
            "INVALID_PARAMETER_TYPE",
            "error.common.invalid-parameter-type"),
    MISSING_REQUIRED_PARAMETER(
            HttpStatus.BAD_REQUEST,
            "MISSING_REQUIRED_PARAMETER",
            "error.common.missing-required-parameter"),
    MALFORMED_REQUEST_BODY(
            HttpStatus.BAD_REQUEST,
            "MALFORMED_REQUEST_BODY",
            "error.common.malformed-request-body"),
    UNSUPPORTED_MEDIA_TYPE(
            HttpStatus.UNSUPPORTED_MEDIA_TYPE,
            "UNSUPPORTED_MEDIA_TYPE",
            "error.common.unsupported-media-type"),
    INVALID_DATE(HttpStatus.BAD_REQUEST, "INVALID_DATE",
            "error.common.invalid-date"),
    INVALID_TIME(HttpStatus.BAD_REQUEST, "INVALID_TIME",
            "error.common.invalid-time"),

    // HTTP Protocol - 경로, 메서드, 응답 형식 및 요청 크기
    RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RESOURCE_NOT_FOUND",
            "error.common.resource-not-found"),
    METHOD_NOT_ALLOWED(
            HttpStatus.METHOD_NOT_ALLOWED,
            "METHOD_NOT_ALLOWED",
            "error.common.method-not-allowed"),
    NOT_ACCEPTABLE(
            HttpStatus.NOT_ACCEPTABLE,
            "NOT_ACCEPTABLE",
            "error.common.not-acceptable"),
    REQUEST_TOO_LARGE(
            HttpStatus.CONTENT_TOO_LARGE,
            "REQUEST_TOO_LARGE",
            "error.common.request-too-large"),
    ASYNC_REQUEST_TIMEOUT(
            HttpStatus.SERVICE_UNAVAILABLE,
            "ASYNC_REQUEST_TIMEOUT",
            "error.common.async-request-timeout"),

    // Data - 공통 데이터 제약 충돌
    DATA_CONFLICT(
            HttpStatus.CONFLICT,
            "DATA_CONFLICT",
            "error.common.data-conflict"),

    // Security - 인증 및 인가
    UNAUTHENTICATED(
            HttpStatus.UNAUTHORIZED,
            "UNAUTHENTICATED",
            "error.common.unauthenticated"),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "ACCESS_DENIED",
            "error.common.access-denied"),

    // Server - 예상하지 못한 내부 오류
    INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INTERNAL_SERVER_ERROR",
            "error.common.internal-server-error");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    CommonErrorCode(HttpStatus status, String code, String messageKey) {
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
