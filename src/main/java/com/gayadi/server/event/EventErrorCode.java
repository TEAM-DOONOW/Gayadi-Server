package com.gayadi.server.event;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EventErrorCode implements ErrorCode {

    // Event - 현장 상황과 일정 변경 대상
    EVENT_INDOOR_ALTERNATIVE_NOT_FOUND(HttpStatus.CONFLICT, "EVENT_INDOOR_ALTERNATIVE_NOT_FOUND",
            "error.event.indoor-alternative-not-found"),
    EVENT_PLAN_ALREADY_CHANGED(HttpStatus.CONFLICT, "EVENT_PLAN_ALREADY_CHANGED",
            "error.event.plan-already-changed"),
    EVENT_SCHEDULE_CHANGE_TARGET_NOT_FOUND(HttpStatus.CONFLICT, "EVENT_SCHEDULE_CHANGE_TARGET_NOT_FOUND",
            "error.event.schedule-change-target-not-found"),
    EVENT_TRIP_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "EVENT_TRIP_NOT_IN_PROGRESS",
            "error.event.trip-not-in-progress"),
    EVENT_PLAN_NOT_FOUND(HttpStatus.CONFLICT, "EVENT_PLAN_NOT_FOUND",
            "error.event.plan-not-found"),
    EVENT_ALTERNATIVE_PLACE_UNAVAILABLE(HttpStatus.CONFLICT, "EVENT_ALTERNATIVE_PLACE_UNAVAILABLE",
            "error.event.alternative-place-unavailable"),

    // Change Proposal - 변경 제안 상태, 대상 일정 및 승인 옵션
    CHANGE_PROPOSAL_ALREADY_DECIDED(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_ALREADY_DECIDED",
            "error.event.change-proposal-already-decided"),
    CHANGE_PROPOSAL_REVISION_MISMATCH(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_REVISION_MISMATCH",
            "error.event.change-proposal-revision-mismatch"),
    CHANGE_PROPOSAL_TARGET_PLAN_NOT_FOUND(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_TARGET_PLAN_NOT_FOUND",
            "error.event.change-proposal-target-plan-not-found"),
    CHANGE_PROPOSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "CHANGE_PROPOSAL_NOT_FOUND",
            "error.event.change-proposal-not-found"),
    CHANGE_PROPOSAL_OPTION_REQUIRED(HttpStatus.BAD_REQUEST, "CHANGE_PROPOSAL_OPTION_REQUIRED",
            "error.event.change-proposal-option-required"),
    CHANGE_PROPOSAL_OPTIONS_MISSING(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_OPTIONS_MISSING",
            "error.event.change-proposal-options-missing"),
    CHANGE_PROPOSAL_OPTIONS_INVALID(HttpStatus.CONFLICT, "CHANGE_PROPOSAL_OPTIONS_INVALID",
            "error.event.change-proposal-options-invalid"),
    CHANGE_PROPOSAL_OPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "CHANGE_PROPOSAL_OPTION_NOT_ALLOWED",
            "error.event.change-proposal-option-not-allowed"),

    // Observation - 현장 관측 장소, 출처 및 상세값
    OBSERVATION_PLACE_OUTSIDE_REGION(HttpStatus.BAD_REQUEST, "OBSERVATION_PLACE_OUTSIDE_REGION",
            "error.event.observation-place-outside-region"),
    OBSERVATION_SOURCE_TOO_LONG(HttpStatus.BAD_REQUEST, "OBSERVATION_SOURCE_TOO_LONG",
            "error.event.observation-source-too-long"),
    OBSERVATION_PAYLOAD_INVALID(HttpStatus.BAD_REQUEST, "OBSERVATION_PAYLOAD_INVALID",
            "error.event.observation-payload-invalid"),

    // Event Type - 현장 상황 종류 매핑
    EVENT_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "EVENT_TYPE_REQUIRED",
            "error.event.type-required"),
    EVENT_TYPE_INVALID(HttpStatus.BAD_REQUEST, "EVENT_TYPE_INVALID",
            "error.event.type-invalid"),
    USER_REQUEST_EVENT_TYPE_REQUIRED(HttpStatus.BAD_REQUEST, "USER_REQUEST_EVENT_TYPE_REQUIRED",
            "error.event.user-request-event-type-required");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    EventErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
