package com.gayadi.server.congestion;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CongestionErrorCode implements ErrorCode {

    // Forecast Request - 혼잡 예측 기준 시각
    CONGESTION_TARGET_AT_INVALID(HttpStatus.BAD_REQUEST, "CONGESTION_TARGET_AT_INVALID",
            "error.congestion.target-at-invalid", "혼잡 예측 시각은 UTC 오프셋을 포함한 ISO-8601 형식이어야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    CongestionErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
