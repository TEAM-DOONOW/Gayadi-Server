package com.gayadi.server.coordination;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CoordinationErrorCode implements ErrorCode {

    // Date Input - 참여 가능한 날짜와 확정 기간 입력
    COORDINATION_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "COORDINATION_DATE_RANGE_INVALID",
            "error.coordination.date-range-invalid"),
    COORDINATION_AVAILABILITY_REQUIRED(HttpStatus.BAD_REQUEST, "COORDINATION_AVAILABILITY_REQUIRED",
            "error.coordination.availability-required"),
    COORDINATION_AVAILABILITY_DATE_OUT_OF_RANGE(HttpStatus.BAD_REQUEST,
            "COORDINATION_AVAILABILITY_DATE_OUT_OF_RANGE",
            "error.coordination.availability-date-out-of-range"),

    // Finalization - 참여자 제출 완료 및 공통 여행 기간 확정
    COORDINATION_SUBMISSIONS_INCOMPLETE(HttpStatus.CONFLICT, "COORDINATION_SUBMISSIONS_INCOMPLETE",
            "error.coordination.submissions-incomplete"),
    COORDINATION_RANGE_NOT_COMMON(HttpStatus.CONFLICT, "COORDINATION_RANGE_NOT_COMMON",
            "error.coordination.range-not-common");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    CoordinationErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
