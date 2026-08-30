package com.gayadi.server.event;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EventErrorCode implements ErrorCode {

    // Event - 현장 상황과 일정 변경 대상
    EVENT_INDOOR_ALTERNATIVE_NOT_FOUND(HttpStatus.CONFLICT, "EVENT_INDOOR_ALTERNATIVE_NOT_FOUND",
            "error.event.indoor-alternative-not-found", "해당 지역에 이용할 수 있는 실내 대체 장소가 없습니다."),
    EVENT_PLAN_ALREADY_CHANGED(HttpStatus.CONFLICT, "EVENT_PLAN_ALREADY_CHANGED",
            "error.event.plan-already-changed", "일정이 이미 변경되었습니다."),
    EVENT_SCHEDULE_CHANGE_TARGET_NOT_FOUND(HttpStatus.CONFLICT, "EVENT_SCHEDULE_CHANGE_TARGET_NOT_FOUND",
            "error.event.schedule-change-target-not-found", "변경할 수 있는 예정 일정이 없습니다."),
    EVENT_TRIP_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "EVENT_TRIP_NOT_IN_PROGRESS",
            "error.event.trip-not-in-progress", "여행 중에만 현장 상황을 처리할 수 있습니다."),
    EVENT_PLAN_NOT_FOUND(HttpStatus.CONFLICT, "EVENT_PLAN_NOT_FOUND",
            "error.event.plan-not-found", "변경할 일정을 찾을 수 없습니다."),
    EVENT_ALTERNATIVE_PLACE_UNAVAILABLE(HttpStatus.CONFLICT, "EVENT_ALTERNATIVE_PLACE_UNAVAILABLE",
            "error.event.alternative-place-unavailable", "선택한 {0}를 더 이상 이용할 수 없습니다."),

    // Change Proposal - 변경 제안 상태, 대상 일정 및 승인 옵션
    CHANGE_PROPOSAL_ALREADY_DECIDED(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_DECIDED",
            "error.event.change-proposal-already-decided", "이미 처리된 변경 제안입니다."),
    CHANGE_PROPOSAL_REVISION_MISMATCH(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_REVISION_MISMATCH",
            "error.event.change-proposal-revision-mismatch", "변경 제안의 일정 버전과 요청한 버전이 다릅니다."),
    CHANGE_PROPOSAL_TARGET_PLAN_NOT_FOUND(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_TARGET_PLAN_NOT_FOUND",
            "error.event.change-proposal-target-plan-not-found", "변경 제안을 만들 일정이 없습니다."),
    CHANGE_PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "CHANGE_PROPOSAL_NOT_FOUND",
            "error.event.change-proposal-not-found", "변경 제안을 찾을 수 없습니다."),
    CHANGE_PROPOSAL_OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "CHANGE_PROPOSAL_OPTION_REQUIRED",
            "error.event.change-proposal-option-required", "승인할 대체 장소를 선택해야 합니다."),
    CHANGE_PROPOSAL_OPTIONS_MISSING(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_OPTIONS_MISSING",
            "error.event.change-proposal-options-missing", "변경 제안의 대체 장소 정보가 없습니다."),
    CHANGE_PROPOSAL_OPTIONS_INVALID(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_OPTIONS_INVALID",
            "error.event.change-proposal-options-invalid", "변경 제안의 대체 장소 정보가 올바르지 않습니다."),
    CHANGE_PROPOSAL_OPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CHANGE_PROPOSAL_OPTION_NOT_ALLOWED",
            "error.event.change-proposal-option-not-allowed", "변경 제안에 포함된 대체 장소만 선택할 수 있습니다."),

    // Observation - 현장 관측 장소, 출처 및 상세값
    OBSERVATION_PLACE_OUTSIDE_REGION(HttpStatus.BAD_REQUEST, "OBSERVATION_PLACE_OUTSIDE_REGION",
            "error.event.observation-place-outside-region", "관측 장소가 여행 지역에 속하지 않습니다."),
    OBSERVATION_SOURCE_TOO_LONG(HttpStatus.BAD_REQUEST, "OBSERVATION_SOURCE_TOO_LONG",
            "error.event.observation-source-too-long", "현장 상황 출처는 50자까지 입력할 수 있습니다."),
    OBSERVATION_PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "OBSERVATION_PAYLOAD_INVALID",
            "error.event.observation-payload-invalid", "현장 상황 상세값은 8KB, 깊이 4단계, 항목 32개 이내여야 합니다."),

    // Event Type - 현장 상황 종류 매핑
    EVENT_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "EVENT_TYPE_REQUIRED",
            "error.event.type-required", "현장 상황 종류가 필요합니다."),
    EVENT_TYPE_INVALID(HttpStatus.BAD_REQUEST, "EVENT_TYPE_INVALID",
            "error.event.type-invalid", "올바르지 않은 현장 상황 종류입니다."),
    USER_REQUEST_EVENT_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "USER_REQUEST_EVENT_TYPE_REQUIRED",
            "error.event.user-request-event-type-required", "사용자 요청 제안에는 현장 관측 종류가 필요합니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    EventErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
