package com.gayadi.server.schedule;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ScheduleErrorCode implements ErrorCode {

    // Schedule Order - 수동 일정 순서 변경
    SCHEDULE_ORDER_REQUIRED(HttpStatus.BAD_REQUEST, "SCHEDULE_ORDER_REQUIRED",
            "error.schedule.order-required", "일정 순서를 하나 이상 보내 주세요."),
    SCHEDULE_ORDER_DUPLICATED(HttpStatus.BAD_REQUEST, "SCHEDULE_ORDER_DUPLICATED",
            "error.schedule.order-duplicated", "같은 일정이 순서 목록에 두 번 들어 있습니다."),
    SCHEDULE_ORDER_INCOMPLETE(HttpStatus.BAD_REQUEST, "SCHEDULE_ORDER_INCOMPLETE",
            "error.schedule.order-incomplete", "현재 여행의 모든 일정을 빠짐없이 보내 주세요."),

    // Schedule Resource - 수동 일정 및 일정표 조회·생성
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_NOT_FOUND",
            "error.schedule.not-found", "일정을 찾을 수 없습니다."),
    SCHEDULE_PLAN_CREATION_CONFLICT(HttpStatus.CONFLICT, "SCHEDULE_PLAN_CREATION_CONFLICT",
            "error.schedule.plan-creation-conflict", "일정표를 만들지 못했습니다."),

    // Schedule Validation - 수동 일정 입력과 여행 상태 검증
    SCHEDULE_DATE_OUTSIDE_TRIP(HttpStatus.BAD_REQUEST, "SCHEDULE_DATE_OUTSIDE_TRIP",
            "error.schedule.date-outside-trip", "일정 날짜는 여행 기간 안이어야 합니다."),
    SCHEDULE_TITLE_INVALID(HttpStatus.BAD_REQUEST, "SCHEDULE_TITLE_INVALID",
            "error.schedule.title-invalid", "일정 이름은 1자에서 200자 사이여야 합니다."),
    SCHEDULE_REQUIRED_FIELDS_MISSING(HttpStatus.BAD_REQUEST, "SCHEDULE_REQUIRED_FIELDS_MISSING",
            "error.schedule.required-fields-missing", "일정 날짜, 시각과 종류가 필요합니다."),
    SCHEDULE_END_TIME_INVALID(HttpStatus.BAD_REQUEST, "SCHEDULE_END_TIME_INVALID",
            "error.schedule.end-time-invalid", "일정 종료 시각은 시작 시각보다 뒤여야 합니다."),
    SCHEDULE_MEMO_TOO_LONG(HttpStatus.BAD_REQUEST, "SCHEDULE_MEMO_TOO_LONG",
            "error.schedule.memo-too-long", "일정 메모는 500자까지 입력할 수 있습니다."),
    SCHEDULE_TRIP_NOT_EDITABLE(HttpStatus.CONFLICT, "SCHEDULE_TRIP_NOT_EDITABLE",
            "error.schedule.trip-not-editable", "완료되거나 취소된 여행의 일정은 바꿀 수 없습니다."),
    SCHEDULE_PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE_PLACE_NOT_FOUND",
            "error.schedule.place-not-found", "장소를 찾을 수 없습니다."),

    // Plan Generation - 자동 일정표 생성 및 추천 조건
    PLAN_GENERATION_TRIP_NOT_PLANNING(HttpStatus.CONFLICT, "PLAN_GENERATION_TRIP_NOT_PLANNING",
            "error.schedule.plan-generation-trip-not-planning", "여행 시작 전 일정만 다시 생성할 수 있습니다."),
    PLAN_TRIP_DATE_INVALID(HttpStatus.CONFLICT, "PLAN_TRIP_DATE_INVALID",
            "error.schedule.plan-trip-date-invalid", "여행 기간이 올바르지 않습니다."),
    PLAN_GENERATION_RANGE_EXCEEDED(HttpStatus.CONFLICT, "PLAN_GENERATION_RANGE_EXCEEDED",
            "error.schedule.plan-generation-range-exceeded", "자동 일정은 최대 366일까지 생성할 수 있습니다."),
    PLAN_PLACE_CANDIDATE_NOT_FOUND(HttpStatus.CONFLICT, "PLAN_PLACE_CANDIDATE_NOT_FOUND",
            "error.schedule.plan-place-candidate-not-found", "해당 지역에 일정으로 추천할 수 있는 장소가 없습니다."),
    PLAN_GENERATION_CONFLICT(HttpStatus.CONFLICT, "PLAN_GENERATION_CONFLICT",
            "error.schedule.plan-generation-conflict", "일정이 동시에 생성되었습니다. 다시 조회해 주세요."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND",
            "error.schedule.plan-not-found", "생성된 일정이 없습니다."),
    PLAN_PLACE_REQUIRED(HttpStatus.CONFLICT, "PLAN_PLACE_REQUIRED",
            "error.schedule.plan-place-required", "경로 계산 전에 장소 일정이 필요합니다."),
    PLAN_PROFILE_INVALID(HttpStatus.CONFLICT, "PLAN_PROFILE_INVALID",
            "error.schedule.plan-profile-invalid", "일정에 반영할 성향 검사 결과가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    ScheduleErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
