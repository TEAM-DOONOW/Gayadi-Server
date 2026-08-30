package com.gayadi.server.common.exception;

import org.springframework.http.HttpStatus;

/** 클라이언트에 공개하는 안정적인 오류 코드의 공통 계약이다. */
public interface ErrorCode {

    HttpStatus status();

    String code();

    String messageKey();

    String defaultMessage();
}
