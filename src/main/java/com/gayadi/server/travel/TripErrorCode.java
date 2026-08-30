package com.gayadi.server.travel;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TripErrorCode implements ErrorCode {

    // Trip Access - 여행 조회, 참여자 및 소유자 권한
    TRIP_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_NOT_FOUND",
            "error.trip.not-found", "여행을 찾을 수 없습니다."),
    TRIP_MEMBER_REQUIRED(HttpStatus.FORBIDDEN, "TRIP_MEMBER_REQUIRED",
            "error.trip.member-required", "여행 참여자만 처리할 수 있습니다."),
    TRIP_OWNER_REQUIRED(HttpStatus.FORBIDDEN, "TRIP_OWNER_REQUIRED",
            "error.trip.owner-required", "여행 소유자만 처리할 수 있습니다."),
    TRIP_OWNER_REMOVAL_FORBIDDEN(HttpStatus.BAD_REQUEST, "TRIP_OWNER_REMOVAL_FORBIDDEN",
            "error.trip.owner-removal-forbidden", "여행 소유자는 내보낼 수 없습니다."),
    TRIP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "TRIP_MEMBER_NOT_FOUND",
            "error.trip.member-not-found", "참여자를 찾을 수 없습니다."),

    // Trip State & Concurrency - 여행 수정 버전과 상태 전이
    TRIP_VERSION_CONFLICT(HttpStatus.CONFLICT, "TRIP_VERSION_CONFLICT",
            "error.trip.version-conflict", "다른 사용자가 여행 정보를 먼저 바꿨습니다. 다시 불러와 주세요."),
    TRIP_SCHEDULE_OUTSIDE_DATE_RANGE(HttpStatus.CONFLICT, "TRIP_SCHEDULE_OUTSIDE_DATE_RANGE",
            "error.trip.schedule-outside-date-range",
            "바꿀 여행 기간 밖에 일정이 있습니다. 일정을 먼저 정리해 주세요."),
    TRIP_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "TRIP_STATUS_TRANSITION_INVALID",
            "error.trip.status-transition-invalid", "여행 상태를 {0}에서 {1}(으)로 변경할 수 없습니다."),
    TRIP_STATUS_CHANGE_CONFLICT(HttpStatus.CONFLICT, "TRIP_STATUS_CHANGE_CONFLICT",
            "error.trip.status-change-conflict", "여행 상태를 바꿀 수 없습니다."),

    // Invitation & Membership - 여행 공유 코드와 참여 조건
    TRIP_INVITE_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "TRIP_INVITE_CODE_UNAVAILABLE",
            "error.trip.invite-code-unavailable", "여행 초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),
    TRIP_NOT_JOINABLE(HttpStatus.CONFLICT, "TRIP_NOT_JOINABLE",
            "error.trip.not-joinable", "준비 중인 여행에만 참여할 수 있습니다."),
    TRIP_MEMBER_CAPACITY_REACHED(HttpStatus.CONFLICT, "TRIP_MEMBER_CAPACITY_REACHED",
            "error.trip.member-capacity-reached", "여행 참여 인원이 가득 찼습니다."),
    TRIP_ALREADY_JOINED(HttpStatus.CONFLICT, "TRIP_ALREADY_JOINED",
            "error.trip.already-joined", "이미 참여한 여행입니다."),

    // Region & City - 여행 도시와 지역 저장
    TRIP_CITY_REQUIRED(HttpStatus.BAD_REQUEST, "TRIP_CITY_REQUIRED",
            "error.trip.city-required", "여행 도시를 하나 이상 골라 주세요."),
    TRIP_REGION_CREATION_CONFLICT(HttpStatus.CONFLICT, "TRIP_REGION_CREATION_CONFLICT",
            "error.trip.region-creation-conflict", "여행 지역을 등록하지 못했습니다."),

    // Trip Validation - 여행 기간, 이름, 출발 방식 및 인원 검증
    TRIP_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "TRIP_DATE_RANGE_INVALID",
            "error.trip.date-range-invalid", "여행 종료일은 시작일과 같거나 뒤여야 합니다."),
    TRIP_DURATION_EXCEEDED(HttpStatus.BAD_REQUEST, "TRIP_DURATION_EXCEEDED",
            "error.trip.duration-exceeded", "한 여행은 31일까지 만들 수 있습니다."),
    TRIP_TITLE_INVALID(HttpStatus.BAD_REQUEST, "TRIP_TITLE_INVALID",
            "error.trip.title-invalid", "여행 이름은 1자에서 100자 사이여야 합니다."),
    TRIP_PLACE_INVALID(HttpStatus.BAD_REQUEST, "TRIP_PLACE_INVALID",
            "error.trip.place-invalid", "출발지나 귀가 장소를 확인할 수 없습니다."),
    TRIP_DEPARTURE_MODE_REQUIRED(HttpStatus.BAD_REQUEST, "TRIP_DEPARTURE_MODE_REQUIRED",
            "error.trip.departure-mode-required", "출발 방식을 골라 주세요."),
    TRIP_MEETING_REQUIRED(HttpStatus.BAD_REQUEST, "TRIP_MEETING_REQUIRED",
            "error.trip.meeting-required", "함께 출발할 때는 모이는 시각과 장소가 필요합니다."),
    TRIP_MAX_MEMBERS_INVALID(HttpStatus.BAD_REQUEST, "TRIP_MAX_MEMBERS_INVALID",
            "error.trip.max-members-invalid", "참여 인원은 1명에서 100명 사이여야 합니다."),
    TRIP_STATUS_INVALID(HttpStatus.BAD_REQUEST, "TRIP_STATUS_INVALID",
            "error.trip.status-invalid", "올바르지 않은 여행 상태입니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    TripErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
