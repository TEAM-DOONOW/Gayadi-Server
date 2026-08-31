package com.gayadi.server.route;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 경로 계산·선택·외부 공급자 오류 코드를 정의합니다. */
public enum RouteErrorCode implements ErrorCode {

    // Calculation - 경로 계산 입력과 선행 일정
    ROUTE_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_TYPE_REQUIRED",
            "error.route.type-required"),
    ROUTE_TYPE_INVALID(HttpStatus.BAD_REQUEST, "ROUTE_TYPE_INVALID",
            "error.route.type-invalid"),
    ROUTE_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "ROUTE_TYPE_MISMATCH",
            "error.route.type-mismatch"),
    ROUTE_MEETING_PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_MEETING_PLACE_REQUIRED",
            "error.route.meeting-place-required"),
    ROUTE_MEMBER_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_MEMBER_REQUIRED",
            "error.route.member-required"),
    ROUTE_DEPARTURE_PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_DEPARTURE_PLACE_REQUIRED",
            "error.route.departure-place-required"),
    ROUTE_RETURN_PLACE_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_RETURN_PLACE_REQUIRED",
            "error.route.return-place-required"),
    ROUTE_ITINERARY_TOO_LARGE(HttpStatus.BAD_REQUEST, "ROUTE_ITINERARY_TOO_LARGE",
            "error.route.itinerary-too-large"),
    ROUTE_PLAN_REQUIRED(HttpStatus.CONFLICT, "ROUTE_PLAN_REQUIRED",
            "error.route.plan-required"),
    ROUTE_STOPS_INSUFFICIENT(HttpStatus.CONFLICT, "ROUTE_STOPS_INSUFFICIENT",
            "error.route.stops-insufficient"),
    ROUTE_CALCULATION_CHANGED(HttpStatus.CONFLICT, "ROUTE_CALCULATION_CHANGED",
            "error.route.calculation-changed"),
    ROUTE_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_PLACE_NOT_FOUND",
            "error.route.place-not-found"),

    // Selection - 추천 경로 조회, 선택 및 상태
    ROUTE_OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "ROUTE_OPTION_REQUIRED",
            "error.route.option-required"),
    ROUTE_OPTION_INVALID(HttpStatus.BAD_REQUEST, "ROUTE_OPTION_INVALID",
            "error.route.option-invalid"),
    ROUTE_RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_RECOMMENDATION_NOT_FOUND",
            "error.route.recommendation-not-found"),
    ROUTE_SELECTABLE_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_SELECTABLE_NOT_FOUND",
            "error.route.selectable-not-found"),
    ROUTE_SELECTION_FORBIDDEN(HttpStatus.FORBIDDEN, "ROUTE_SELECTION_FORBIDDEN",
            "error.route.selection-forbidden"),
    ROUTE_ACCESS_FORBIDDEN(HttpStatus.FORBIDDEN, "ROUTE_ACCESS_FORBIDDEN",
            "error.route.access-forbidden"),
    ROUTE_NOT_SELECTABLE(HttpStatus.CONFLICT, "ROUTE_NOT_SELECTABLE",
            "error.route.not-selectable"),
    ROUTE_GROUP_MEMBER_FORBIDDEN(HttpStatus.BAD_REQUEST, "ROUTE_GROUP_MEMBER_FORBIDDEN",
            "error.route.group-member-forbidden"),
    ROUTE_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "ROUTE_MEMBER_NOT_FOUND",
            "error.route.member-not-found"),
    ROUTE_MEMBER_DATA_MISSING(HttpStatus.CONFLICT, "ROUTE_MEMBER_DATA_MISSING",
            "error.route.member-data-missing"),

    // Provider - 경로 계산기 및 TMAP 외부 연동
    ROUTE_PROVIDER_FAILED(HttpStatus.BAD_GATEWAY, "ROUTE_PROVIDER_FAILED",
            "error.route.provider-failed"),
    TMAP_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "TMAP_NOT_CONFIGURED",
            "error.route.tmap-not-configured"),
    TMAP_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "TMAP_REQUEST_FAILED",
            "error.route.tmap-request-failed"),
    TMAP_AUTH_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "TMAP_AUTH_FAILED",
            "error.route.tmap-auth-failed"),
    TMAP_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "TMAP_RATE_LIMITED",
            "error.route.tmap-rate-limited"),
    TMAP_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "TMAP_RESPONSE_INVALID",
            "error.route.tmap-response-invalid"),
    TMAP_ROUTE_UNAVAILABLE(HttpStatus.CONFLICT, "TMAP_ROUTE_UNAVAILABLE",
            "error.route.tmap-route-unavailable");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    RouteErrorCode(HttpStatus status, String code, String messageKey) {
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
