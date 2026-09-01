package com.gayadi.server.weather;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 기상 조회 검증과 외부 기상청 API 오류 코드를 정의합니다. */
public enum WeatherErrorCode implements ErrorCode {

    // Request - 발표 기준과 조회 파라미터
    WEATHER_BASE_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "WEATHER_BASE_PAIR_REQUIRED",
            "error.weather.base-pair-required"),
    WEATHER_BASE_DATETIME_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_BASE_DATETIME_INVALID",
            "error.weather.base-datetime-invalid"),
    WEATHER_BASE_TIME_UNAVAILABLE(HttpStatus.BAD_REQUEST, "WEATHER_BASE_TIME_UNAVAILABLE",
            "error.weather.base-time-unavailable"),
    WEATHER_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_REQUEST_INVALID",
            "error.weather.request-invalid"),
    WEATHER_REQUIRED_PARAMETER_MISSING(HttpStatus.BAD_REQUEST, "WEATHER_REQUIRED_PARAMETER_MISSING",
            "error.weather.required-parameter-missing"),
    WEATHER_LOCATION_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_PAIR_REQUIRED",
            "error.weather.location-pair-required"),
    WEATHER_LOCATION_TYPE_CONFLICT(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_TYPE_CONFLICT",
            "error.weather.location-type-conflict"),
    WEATHER_GRID_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "WEATHER_GRID_OUT_OF_RANGE",
            "error.weather.grid-out-of-range"),
    WEATHER_COORDINATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "WEATHER_COORDINATE_OUT_OF_RANGE",
            "error.weather.coordinate-out-of-range"),
    WEATHER_LOCATION_UNSUPPORTED(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_UNSUPPORTED",
            "error.weather.location-unsupported"),
    WEATHER_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_REQUIRED",
            "error.weather.location-required"),
    WEATHER_VERSION_FILE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_VERSION_FILE_TYPE_INVALID",
            "error.weather.version-file-type-invalid"),
    WEATHER_VERSION_DATETIME_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_VERSION_DATETIME_INVALID",
            "error.weather.version-datetime-invalid"),

    // External API - 기상청 호출, 인증 및 응답
    WEATHER_API_INTERRUPTED(HttpStatus.BAD_GATEWAY, "WEATHER_API_INTERRUPTED",
            "error.weather.api-interrupted"),
    WEATHER_API_FAILED(HttpStatus.BAD_GATEWAY, "WEATHER_API_FAILED",
            "error.weather.api-failed"),
    WEATHER_API_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "WEATHER_API_RATE_LIMITED",
            "error.weather.api-rate-limited"),
    WEATHER_API_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "WEATHER_API_RESPONSE_INVALID",
            "error.weather.api-response-invalid"),
    WEATHER_API_AUTH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "WEATHER_API_AUTH_FAILED",
            "error.weather.api-auth-failed"),
    WEATHER_API_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "WEATHER_API_NOT_CONFIGURED",
            "error.weather.api-not-configured"),
    WEATHER_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "WEATHER_DATA_NOT_FOUND",
            "error.weather.data-not-found");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    WeatherErrorCode(HttpStatus status, String code, String messageKey) {
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
