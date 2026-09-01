package com.gayadi.server.expense.query;

import com.gayadi.server.expense.model.ExpenseCategory;
import com.gayadi.server.expense.model.ExpensePaymentSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 여행 경비와 정산 Repository의 ExpenseQueryResult 조회 결과를 전달합니다. */
public record ExpenseQueryResult(
        long id,
        long tripId,
        Long scheduleId,
        String title,
        String memo,
        long amount,
        Long payerId,
        LocalDate date,
        LocalTime time,
        ExpenseCategory category,
        ExpensePaymentSource paymentSource,
        String receiptImageUri,
        long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
