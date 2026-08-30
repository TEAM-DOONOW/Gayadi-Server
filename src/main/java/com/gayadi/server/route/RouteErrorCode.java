package com.gayadi.server.route;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RouteErrorCode implements ErrorCode {

    // Calculation - 경로 계산 입력과 선행 일정
    ROUTE_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_TYPE_REQUIRED",
            "error.route.type-required", "경로 종류를 골라 주세요."),
    ROUTE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "ROUTE_TYPE_INVALID",
            "error.route.type-invalid", "올바르지 않은 경로 종류입니다."),
    ROUTE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "ROUTE_TYPE_MISMATCH",
            "error.route.type-mismatch", "경로 종류와 추천 경로가 서로 다릅니다."),
    ROUTE_MEETING_PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_MEETING_PLACE_REQUIRED",
            "error.route.meeting-place-required", "집결 장소가 설정되지 않았습니다."),
    ROUTE_MEMBER_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_MEMBER_REQUIRED",
            "error.route.member-required", "개인 경로에는 참여자 식별자가 필요합니다."),
    ROUTE_DEPARTURE_PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_DEPARTURE_PLACE_REQUIRED",
            "error.route.departure-place-required", "출발 장소가 설정되지 않았습니다."),
    ROUTE_RETURN_PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_RETURN_PLACE_REQUIRED",
            "error.route.return-place-required", "귀가 장소가 설정되지 않았습니다."),
    ROUTE_ITINERARY_TOO_LARGE(HttpStatus.BAD_REQUEST, "ROUTE_ITINERARY_TOO_LARGE",
            "error.route.itinerary-too-large", "여행 동선은 장소 일정 100개까지 계산할 수 있습니다."),
    ROUTE_PLAN_REQUIRED(HttpStatus.CONFLICT, "ROUTE_PLAN_REQUIRED",
            "error.route.plan-required", "경로 계산 전에 일정이 필요합니다."),
    ROUTE_STOPS_INSUFFICIENT(HttpStatus.CONFLICT, "ROUTE_STOPS_INSUFFICIENT",
            "error.route.stops-insufficient", "여행 동선을 계산하려면 장소 일정이 두 개 이상 필요합니다."),
    ROUTE_CALCULATION_CHANGED(HttpStatus.CONFLICT, "ROUTE_CALCULATION_CHANGED",
            "error.route.calculation-changed", "경로를 계산하는 동안 일정 또는 참여자 정보가 바뀌었습니다. 다시 요청해 주세요."),
    ROUTE_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_PLACE_NOT_FOUND",
            "error.route.place-not-found", "경로에 필요한 장소를 찾을 수 없습니다."),

    // Selection - 추천 경로 조회, 선택 및 상태
    ROUTE_OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_OPTION_REQUIRED",
            "error.route.option-required", "선택할 경로 번호나 선택안 값을 보내 주세요."),
    ROUTE_OPTION_INVALID(HttpStatus.BAD_REQUEST, "ROUTE_OPTION_INVALID",
            "error.route.option-invalid", "경로 종류에 맞는 선택안 값을 보내 주세요."),
    ROUTE_RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_RECOMMENDATION_NOT_FOUND",
            "error.route.recommendation-not-found", "추천 경로를 찾을 수 없습니다."),
    ROUTE_SELECTABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_SELECTABLE_NOT_FOUND",
            "error.route.selectable-not-found", "선택할 수 있는 추천 경로를 찾지 못했습니다. 경로를 다시 추천받아 주세요."),
    ROUTE_SELECTION_FORBIDDEN(HttpStatus.FORBIDDEN, "ROUTE_SELECTION_FORBIDDEN",
            "error.route.selection-forbidden", "자신의 경로만 선택할 수 있습니다."),
    ROUTE_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "ROUTE_ACCESS_FORBIDDEN",
            "error.route.access-forbidden", "자신의 경로만 조회하거나 바꿀 수 있습니다."),
    ROUTE_NOT_SELECTABLE(HttpStatus.CONFLICT, "ROUTE_NOT_SELECTABLE",
            "error.route.not-selectable", "이 경로는 더 이상 선택할 수 없습니다."),
    ROUTE_GROUP_MEMBER_FORBIDDEN(HttpStatus.BAD_REQUEST, "ROUTE_GROUP_MEMBER_FORBIDDEN",
            "error.route.group-member-forbidden", "여행 동선은 참여자 번호 없이 여행 전체 기준으로 요청해 주세요."),
    ROUTE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_MEMBER_NOT_FOUND",
            "error.route.member-not-found", "여행 참여자를 찾을 수 없습니다."),
    ROUTE_MEMBER_DATA_MISSING(HttpStatus.CONFLICT, "ROUTE_MEMBER_DATA_MISSING",
            "error.route.member-data-missing", "경로의 참여자 정보를 찾을 수 없습니다."),

    // Provider - 경로 계산기 및 TMAP 외부 연동
    ROUTE_PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "ROUTE_PROVIDER_FAILED",
            "error.route.provider-failed", "경로 계산 결과가 올바르지 않습니다."),
    TMAP_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "TMAP_NOT_CONFIGURED",
            "error.route.tmap-not-configured", "TMAP 대중교통 API가 설정되지 않았습니다."),
    TMAP_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "TMAP_REQUEST_FAILED",
            "error.route.tmap-request-failed", "TMAP 대중교통 API 호출에 실패했습니다."),
    TMAP_AUTH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "TMAP_AUTH_FAILED",
            "error.route.tmap-auth-failed", "TMAP 대중교통 API 인증에 실패했습니다."),
    TMAP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "TMAP_RATE_LIMITED",
            "error.route.tmap-rate-limited", "TMAP 대중교통 API 호출 한도를 초과했습니다."),
    TMAP_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "TMAP_RESPONSE_INVALID",
            "error.route.tmap-response-invalid", "TMAP 대중교통 API 응답이 올바르지 않습니다."),
    TMAP_ROUTE_UNAVAILABLE(HttpStatus.CONFLICT, "TMAP_ROUTE_UNAVAILABLE",
            "error.route.tmap-route-unavailable", "이동 가능한 TMAP 대중교통 경로가 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    RouteErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
