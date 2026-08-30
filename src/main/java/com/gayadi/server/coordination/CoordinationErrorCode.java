package com.gayadi.server.coordination;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CoordinationErrorCode implements ErrorCode {

    // Date Input - 참여 가능한 날짜와 확정 기간 입력
    COORDINATION_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "COORDINATION_DATE_RANGE_INVALID",
            "error.coordination.date-range-invalid", "여행 종료일은 시작일보다 빠를 수 없습니다."),
    COORDINATION_AVAILABILITY_REQUIRED(HttpStatus.BAD_REQUEST, "COORDINATION_AVAILABILITY_REQUIRED",
            "error.coordination.availability-required", "가능한 날짜를 하나 이상 선택해 주세요."),
    COORDINATION_AVAILABILITY_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST,
            "COORDINATION_AVAILABILITY_DATE_OUT_OF_RANGE",
            "error.coordination.availability-date-out-of-range",
            "가능한 날짜는 오늘부터 2년 이내에서 선택해 주세요."),

    // Finalization - 참여자 제출 완료 및 공통 여행 기간 확정
    COORDINATION_SUBMISSIONS_INCOMPLETE(HttpStatus.CONFLICT, "COORDINATION_SUBMISSIONS_INCOMPLETE",
            "error.coordination.submissions-incomplete",
            "모든 참여자가 가능한 날짜를 제출한 뒤 확정할 수 있습니다."),
    COORDINATION_RANGE_NOT_COMMON(HttpStatus.CONFLICT, "COORDINATION_RANGE_NOT_COMMON",
            "error.coordination.range-not-common",
            "모든 참여자가 가능한 연속 날짜만 여행 기간으로 확정할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    CoordinationErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
