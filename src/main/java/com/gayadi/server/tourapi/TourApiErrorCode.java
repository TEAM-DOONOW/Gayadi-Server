package com.gayadi.server.tourapi;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TourApiErrorCode implements ErrorCode {

    // Request & Region - 요청 파라미터와 여행 지역 매핑
    TOUR_PARAMETER_REQUIRED(HttpStatus.BAD_REQUEST, "TOUR_PARAMETER_REQUIRED",
            "error.tour.parameter-required", "필수 관광 API 파라미터가 없습니다."),
    TOUR_REGION_UNSUPPORTED(HttpStatus.BAD_REQUEST, "TOUR_REGION_UNSUPPORTED",
            "error.tour.region-unsupported", "지원하지 않는 여행 지역입니다."),
    TOUR_REGION_CODE_NOT_FOUND(HttpStatus.BAD_GATEWAY, "TOUR_REGION_CODE_NOT_FOUND",
            "error.tour.region-code-not-found", "관광 API에서 여행 지역의 법정동 코드를 찾지 못했습니다."),
    TOUR_REQUEST_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "TOUR_REQUEST_BUSY",
            "error.tour.request-busy", "관광 정보 요청이 많습니다. 잠시 후 다시 시도해주세요."),

    // External API - 관광 API 호출, 설정 및 응답
    TOUR_API_INTERRUPTED(HttpStatus.BAD_GATEWAY, "TOUR_API_INTERRUPTED",
            "error.tour.api-interrupted", "관광 API 호출이 중단되었습니다."),
    TOUR_API_FAILED(HttpStatus.BAD_GATEWAY, "TOUR_API_FAILED",
            "error.tour.api-failed", "관광 API 호출에 실패했습니다."),
    TOUR_API_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "TOUR_API_RATE_LIMITED",
            "error.tour.api-rate-limited", "관광 API 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    TOUR_API_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "TOUR_API_RESPONSE_INVALID",
            "error.tour.api-response-invalid", "관광 API 응답이 올바르지 않습니다."),
    TOUR_API_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "TOUR_API_NOT_CONFIGURED",
            "error.tour.api-not-configured", "관광 API가 설정되지 않았습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    TourApiErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
