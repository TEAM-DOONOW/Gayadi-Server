package com.gayadi.server.schedule;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ScheduleErrorCode implements ErrorCode {

    // Schedule Order - 수동 일정 순서 변경
    SCHEDULE_ORDER_REQUIRED(HttpStatus.BAD_REQUEST, "SCHEDULE_ORDER_REQUIRED",
            "error.schedule.order-required"),
    SCHEDULE_ORDER_DUPLICATED(HttpStatus.BAD_REQUEST, "SCHEDULE_ORDER_DUPLICATED",
            "error.schedule.order-duplicated"),
    SCHEDULE_ORDER_INCOMPLETE(HttpStatus.BAD_REQUEST, "SCHEDULE_ORDER_INCOMPLETE",
            "error.schedule.order-incomplete"),

    // Schedule Resource - 수동 일정 및 일정표 조회·생성
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND",
            "error.schedule.not-found"),
    SCHEDULE_PLAN_CREATION_CONFLICT(HttpStatus.CONFLICT, "SCHEDULE_PLAN_CREATION_CONFLICT",
            "error.schedule.plan-creation-conflict"),

    // Schedule Validation - 수동 일정 입력과 여행 상태 검증
    SCHEDULE_DATE_OUTSIDE_TRIP(HttpStatus.BAD_REQUEST, "SCHEDULE_DATE_OUTSIDE_TRIP",
            "error.schedule.date-outside-trip"),
    SCHEDULE_TITLE_INVALID(HttpStatus.BAD_REQUEST, "SCHEDULE_TITLE_INVALID",
            "error.schedule.title-invalid"),
    SCHEDULE_REQUIRED_FIELDS_MISSING(HttpStatus.BAD_REQUEST, "SCHEDULE_REQUIRED_FIELDS_MISSING",
            "error.schedule.required-fields-missing"),
    SCHEDULE_END_TIME_INVALID(HttpStatus.BAD_REQUEST, "SCHEDULE_END_TIME_INVALID",
            "error.schedule.end-time-invalid"),
    SCHEDULE_MEMO_TOO_LONG(HttpStatus.BAD_REQUEST, "SCHEDULE_MEMO_TOO_LONG",
            "error.schedule.memo-too-long"),
    SCHEDULE_TRIP_NOT_EDITABLE(HttpStatus.CONFLICT, "SCHEDULE_TRIP_NOT_EDITABLE",
            "error.schedule.trip-not-editable"),
    SCHEDULE_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_PLACE_NOT_FOUND",
            "error.schedule.place-not-found"),

    // Plan Generation - 자동 일정표 생성 및 추천 조건
    PLAN_GENERATION_TRIP_NOT_PLANNING(HttpStatus.CONFLICT, "PLAN_GENERATION_TRIP_NOT_PLANNING",
            "error.schedule.plan-generation-trip-not-planning"),
    PLAN_TRIP_DATE_INVALID(HttpStatus.CONFLICT, "PLAN_TRIP_DATE_INVALID",
            "error.schedule.plan-trip-date-invalid"),
    PLAN_GENERATION_RANGE_EXCEEDED(HttpStatus.CONFLICT, "PLAN_GENERATION_RANGE_EXCEEDED",
            "error.schedule.plan-generation-range-exceeded"),
    PLAN_PLACE_CANDIDATE_NOT_FOUND(HttpStatus.CONFLICT, "PLAN_PLACE_CANDIDATE_NOT_FOUND",
            "error.schedule.plan-place-candidate-not-found"),
    PLAN_GENERATION_CONFLICT(HttpStatus.CONFLICT, "PLAN_GENERATION_CONFLICT",
            "error.schedule.plan-generation-conflict"),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
            "error.schedule.plan-not-found"),
    PLAN_PLACE_REQUIRED(HttpStatus.CONFLICT, "PLAN_PLACE_REQUIRED",
            "error.schedule.plan-place-required"),
    PLAN_PROFILE_INVALID(HttpStatus.CONFLICT, "PLAN_PROFILE_INVALID",
            "error.schedule.plan-profile-invalid");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    ScheduleErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
