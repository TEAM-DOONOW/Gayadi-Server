package com.gayadi.server.expense;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 여행 경비와 정산 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum ExpenseErrorCode implements ErrorCode {

    // Expense - 경비 내역 및 여행 기간 검증
    EXPENSE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "EXPENSE_NOT_FOUND",
            "error.expense.not-found"),
    EXPENSE_DATE_OUTSIDE_TRIP(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_DATE_OUTSIDE_TRIP",
            "error.expense.date-outside-trip"),

    // Participant & Payment - 분담 참여자와 결제 정보 검증
    EXPENSE_PARTICIPANT_DUPLICATED(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_PARTICIPANT_DUPLICATED",
            "error.expense.participant-duplicated"),
    EXPENSE_PARTICIPANT_INVALID(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_PARTICIPANT_INVALID",
            "error.expense.participant-invalid"),
    EXPENSE_PAYER_REQUIRED(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_PAYER_REQUIRED",
            "error.expense.payer-required"),
    EXPENSE_SCHEDULE_INVALID(
            HttpStatus.BAD_REQUEST,
            "EXPENSE_SCHEDULE_INVALID",
            "error.expense.schedule-invalid"),

    // Shared Fund - 공동 경비 잔액
    SHARED_FUND_BALANCE_INSUFFICIENT(
            HttpStatus.CONFLICT,
            "SHARED_FUND_BALANCE_INSUFFICIENT",
            "error.expense.shared-fund-balance-insufficient");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    ExpenseErrorCode(HttpStatus status, String code, String messageKey) {
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
