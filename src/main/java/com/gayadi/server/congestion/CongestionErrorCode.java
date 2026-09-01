package com.gayadi.server.congestion;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 혼잡도 예측 요청과 외부 제공기관 오류 코드를 정의합니다. */
public enum CongestionErrorCode implements ErrorCode {

    // Forecast Request - 혼잡 예측 기준 시각
    CONGESTION_TARGET_AT_INVALID(HttpStatus.BAD_REQUEST, "CONGESTION_TARGET_AT_INVALID",
            "error.congestion.target-at-invalid");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    CongestionErrorCode(HttpStatus status, String code, String messageKey) {
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
