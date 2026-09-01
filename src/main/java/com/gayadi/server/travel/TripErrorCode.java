package com.gayadi.server.travel;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 여행과 참여자 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum TripErrorCode implements ErrorCode {

    // Trip Access - 여행 조회, 참여자 및 소유자 권한
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND",
            "error.trip.not-found"),
    TRIP_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "TRIP_MEMBER_REQUIRED",
            "error.trip.member-required"),
    TRIP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "TRIP_OWNER_REQUIRED",
            "error.trip.owner-required"),
    TRIP_OWNER_REMOVAL_FORBIDDEN(HttpStatus.BAD_REQUEST, "TRIP_OWNER_REMOVAL_FORBIDDEN",
            "error.trip.owner-removal-forbidden"),
    TRIP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_MEMBER_NOT_FOUND",
            "error.trip.member-not-found"),

    // Trip State & Concurrency - 여행 수정 버전과 상태 전이
    TRIP_VERSION_CONFLICT(HttpStatus.CONFLICT, "TRIP_VERSION_CONFLICT",
            "error.trip.version-conflict"),
    TRIP_SCHEDULE_OUTSIDE_DATE_RANGE(HttpStatus.CONFLICT, "TRIP_SCHEDULE_OUTSIDE_DATE_RANGE",
            "error.trip.schedule-outside-date-range"),
    TRIP_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "TRIP_STATUS_TRANSITION_INVALID",
            "error.trip.status-transition-invalid"),
    TRIP_STATUS_CHANGE_CONFLICT(HttpStatus.CONFLICT, "TRIP_STATUS_CHANGE_CONFLICT",
            "error.trip.status-change-conflict"),

    // Invitation & Membership - 여행 공유 코드와 참여 조건
    TRIP_INVITE_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TRIP_INVITE_CODE_UNAVAILABLE",
            "error.trip.invite-code-unavailable"),
    TRIP_NOT_JOINABLE(HttpStatus.CONFLICT, "TRIP_NOT_JOINABLE",
            "error.trip.not-joinable"),
    TRIP_MEMBER_CAPACITY_REACHED(HttpStatus.CONFLICT, "TRIP_MEMBER_CAPACITY_REACHED",
            "error.trip.member-capacity-reached"),
    TRIP_ALREADY_JOINED(HttpStatus.CONFLICT, "TRIP_ALREADY_JOINED",
            "error.trip.already-joined"),

    // Region & City - 여행 도시와 지역 저장
    TRIP_CITY_REQUIRED(HttpStatus.BAD_REQUEST, "TRIP_CITY_REQUIRED",
            "error.trip.city-required"),
    TRIP_REGION_CREATION_CONFLICT(HttpStatus.CONFLICT, "TRIP_REGION_CREATION_CONFLICT",
            "error.trip.region-creation-conflict"),

    // Trip Validation - 여행 기간, 이름, 출발 방식 및 인원 검증
    TRIP_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "TRIP_DATE_RANGE_INVALID",
            "error.trip.date-range-invalid"),
    TRIP_DURATION_EXCEEDED(HttpStatus.BAD_REQUEST, "TRIP_DURATION_EXCEEDED",
            "error.trip.duration-exceeded"),
    TRIP_TITLE_INVALID(HttpStatus.BAD_REQUEST, "TRIP_TITLE_INVALID",
            "error.trip.title-invalid"),
    TRIP_PLACE_INVALID(HttpStatus.BAD_REQUEST, "TRIP_PLACE_INVALID",
            "error.trip.place-invalid"),
    TRIP_DEPARTURE_MODE_REQUIRED(HttpStatus.BAD_REQUEST, "TRIP_DEPARTURE_MODE_REQUIRED",
            "error.trip.departure-mode-required"),
    TRIP_MEETING_REQUIRED(HttpStatus.BAD_REQUEST, "TRIP_MEETING_REQUIRED",
            "error.trip.meeting-required"),
    TRIP_MAX_MEMBERS_INVALID(HttpStatus.BAD_REQUEST, "TRIP_MAX_MEMBERS_INVALID",
            "error.trip.max-members-invalid"),
    TRIP_STATUS_INVALID(HttpStatus.BAD_REQUEST, "TRIP_STATUS_INVALID",
            "error.trip.status-invalid");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    TripErrorCode(HttpStatus status, String code, String messageKey) {
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
