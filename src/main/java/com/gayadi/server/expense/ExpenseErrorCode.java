package com.gayadi.server.expense;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ExpenseErrorCode implements ErrorCode {

    // Expense - 경비 내역 및 여행 기간 검증
    EXPENSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "EXPENSE_NOT_FOUND",
            "error.expense.not-found",
            "경비 내역을 찾을 수 없습니다."),
    EXPENSE_DATE_OUTSIDE_TRIP(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_DATE_OUTSIDE_TRIP",
            "error.expense.date-outside-trip",
            "지출 날짜는 여행 기간 안에서 선택해 주세요."),

    // Participant & Payment - 분담 참여자와 결제 정보 검증
    EXPENSE_PARTICIPANT_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_PARTICIPANT_DUPLICATED",
            "error.expense.participant-duplicated",
            "분담 참여자는 중복될 수 없습니다."),
    EXPENSE_PARTICIPANT_INVALID(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_PARTICIPANT_INVALID",
            "error.expense.participant-invalid",
            "현재 여행에 참여 중인 사용자만 경비에 포함할 수 있습니다."),
    EXPENSE_PAYER_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_PAYER_REQUIRED",
            "error.expense.payer-required",
            "개인 결제의 결제자를 선택해 주세요."),
    EXPENSE_SCHEDULE_INVALID(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_SCHEDULE_INVALID",
            "error.expense.schedule-invalid",
            "현재 여행에 속한 일정만 경비와 연결할 수 있습니다."),

    // Shared Fund - 공동 경비 잔액
    SHARED_FUND_BALANCE_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "SHARED_FUND_BALANCE_INSUFFICIENT",
            "error.expense.shared-fund-balance-insufficient",
            "공동 경비 잔액이 부족합니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    ExpenseErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
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

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
