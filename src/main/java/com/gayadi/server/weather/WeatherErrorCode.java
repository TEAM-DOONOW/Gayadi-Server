package com.gayadi.server.weather;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum WeatherErrorCode implements ErrorCode {

    // Request - 발표 기준과 조회 파라미터
    WEATHER_BASE_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "WEATHER_BASE_PAIR_REQUIRED",
            "error.weather.base-pair-required", "baseDate와 baseTime은 함께 입력해야 합니다."),
    WEATHER_BASE_DATETIME_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_BASE_DATETIME_INVALID",
            "error.weather.base-datetime-invalid", "baseDate는 YYYYMMDD, baseTime은 HHMM 형식이어야 합니다."),
    WEATHER_BASE_TIME_UNAVAILABLE(HttpStatus.BAD_REQUEST, "WEATHER_BASE_TIME_UNAVAILABLE",
            "error.weather.base-time-unavailable", "선택한 조회 종류에서 사용할 수 없는 발표시각입니다."),
    WEATHER_REQUEST_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_REQUEST_INVALID",
            "error.weather.request-invalid", "기상 조회 요청값이 올바르지 않습니다."),
    WEATHER_REQUIRED_PARAMETER_MISSING(HttpStatus.BAD_REQUEST, "WEATHER_REQUIRED_PARAMETER_MISSING",
            "error.weather.required-parameter-missing", "필수 기상 조회 파라미터가 없습니다."),
    WEATHER_LOCATION_PAIR_REQUIRED(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_PAIR_REQUIRED",
            "error.weather.location-pair-required", "lat/lon 또는 nx/ny는 각 쌍을 함께 입력해야 합니다."),
    WEATHER_LOCATION_TYPE_CONFLICT(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_TYPE_CONFLICT",
            "error.weather.location-type-conflict", "lat/lon과 nx/ny 중 한 가지 위치 형식만 입력해야 합니다."),
    WEATHER_GRID_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "WEATHER_GRID_OUT_OF_RANGE",
            "error.weather.grid-out-of-range", "기상청 격자는 nx 1~149, ny 1~253 범위여야 합니다."),
    WEATHER_COORDINATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST, "WEATHER_COORDINATE_OUT_OF_RANGE",
            "error.weather.coordinate-out-of-range", "위도는 -90~90, 경도는 -180~180 범위의 유한한 값이어야 합니다."),
    WEATHER_LOCATION_UNSUPPORTED(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_UNSUPPORTED",
            "error.weather.location-unsupported", "기상청 단기예보가 제공되는 국내 위치를 입력해야 합니다."),
    WEATHER_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "WEATHER_LOCATION_REQUIRED",
            "error.weather.location-required", "위치 정보가 필요합니다. lat/lon 또는 nx/ny 중 하나를 지정하세요."),
    WEATHER_VERSION_FILE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_VERSION_FILE_TYPE_INVALID",
            "error.weather.version-file-type-invalid", "ftype은 ODAM, VSRT, SHRT 중 하나여야 합니다."),
    WEATHER_VERSION_DATETIME_INVALID(HttpStatus.BAD_REQUEST, "WEATHER_VERSION_DATETIME_INVALID",
            "error.weather.version-datetime-invalid", "baseDateTime은 유효한 YYYYMMDDHHMM 형식이어야 합니다."),

    // External API - 기상청 호출, 인증 및 응답
    WEATHER_API_INTERRUPTED(HttpStatus.BAD_GATEWAY, "WEATHER_API_INTERRUPTED",
            "error.weather.api-interrupted", "기상청 API 호출이 중단되었습니다."),
    WEATHER_API_FAILED(HttpStatus.BAD_GATEWAY, "WEATHER_API_FAILED",
            "error.weather.api-failed", "기상청 API 호출에 실패했습니다."),
    WEATHER_API_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "WEATHER_API_RATE_LIMITED",
            "error.weather.api-rate-limited", "기상청 API 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."),
    WEATHER_API_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "WEATHER_API_RESPONSE_INVALID",
            "error.weather.api-response-invalid", "기상청 API 응답이 올바르지 않습니다."),
    WEATHER_API_AUTH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "WEATHER_API_AUTH_FAILED",
            "error.weather.api-auth-failed", "기상청 API 인증에 실패했습니다."),
    WEATHER_API_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "WEATHER_API_NOT_CONFIGURED",
            "error.weather.api-not-configured", "기상청 API가 설정되지 않았습니다."),
    WEATHER_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "WEATHER_DATA_NOT_FOUND",
            "error.weather.data-not-found", "해당 시간의 기상 데이터가 없습니다. 발표 시각을 확인하세요.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    WeatherErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
