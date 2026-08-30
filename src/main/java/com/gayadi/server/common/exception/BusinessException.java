package com.gayadi.server.common.exception;

import java.util.Arrays;
import java.util.Objects;

/**
 * 예상 가능한 비즈니스 실패를 ErrorCode와 함께 전달한다.
 * HTTP 응답 생성은 Global Exception Handler가 담당한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object[] messageArguments;

    public BusinessException(ErrorCode errorCode, Object... messageArguments) {
        super(Objects.requireNonNull(errorCode, "errorCode는 필수입니다.").code());
        this.errorCode = errorCode;
        this.messageArguments = messageArguments == null
                ? new Object[0]
                : Arrays.copyOf(messageArguments, messageArguments.length);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object[] getMessageArguments() {
        return Arrays.copyOf(messageArguments, messageArguments.length);
    }
}
