package com.gayadi.server.tourapi;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TourApiErrorCode implements ErrorCode {

    // Request & Region - 요청 파라미터와 여행 지역 매핑
    TOUR_PARAMETER_REQUIRED(HttpStatus.BAD_REQUEST, "TOUR_PARAMETER_REQUIRED",
            "error.tour.parameter-required"),
    TOUR_REGION_UNSUPPORTED(HttpStatus.BAD_REQUEST, "TOUR_REGION_UNSUPPORTED",
            "error.tour.region-unsupported"),
    TOUR_REGION_CODE_NOT_FOUND(HttpStatus.BAD_GATEWAY, "TOUR_REGION_CODE_NOT_FOUND",
            "error.tour.region-code-not-found"),
    TOUR_REQUEST_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "TOUR_REQUEST_BUSY",
            "error.tour.request-busy"),

    // External API - 관광 API 호출, 설정 및 응답
    TOUR_API_INTERRUPTED(HttpStatus.BAD_GATEWAY, "TOUR_API_INTERRUPTED",
            "error.tour.api-interrupted"),
    TOUR_API_FAILED(HttpStatus.BAD_GATEWAY, "TOUR_API_FAILED",
            "error.tour.api-failed"),
    TOUR_API_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "TOUR_API_RATE_LIMITED",
            "error.tour.api-rate-limited"),
    TOUR_API_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "TOUR_API_RESPONSE_INVALID",
            "error.tour.api-response-invalid"),
    TOUR_API_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "TOUR_API_NOT_CONFIGURED",
            "error.tour.api-not-configured");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    TourApiErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
