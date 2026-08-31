package com.gayadi.server.legal;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 법률 문서 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum LegalErrorCode implements ErrorCode {

    // Document - 법적 문서 식별자 및 조회
    LEGAL_DOCUMENT_ID_INVALID(HttpStatus.BAD_REQUEST, "LEGAL_DOCUMENT_ID_INVALID",
            "error.legal.document-id-invalid"),
    LEGAL_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "LEGAL_DOCUMENT_NOT_FOUND",
            "error.legal.document-not-found");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    LegalErrorCode(HttpStatus status, String code, String messageKey) {
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
