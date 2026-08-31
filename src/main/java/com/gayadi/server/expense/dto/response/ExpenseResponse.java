package com.gayadi.server.expense.dto.response;

import com.gayadi.server.expense.model.ExpenseCategory;
import com.gayadi.server.expense.model.ExpensePaymentSource;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** ExpenseResponse API 응답 데이터를 반환합니다. */
@Schema(name = "TripExpenseResponse", description = "여행 경비 내역")
public record ExpenseResponse(
        long id,
        long tripId,
        Long scheduleId,
        String title,
        String memo,
        long amount,
        Long payerId,
        List<Long> participantIds,
        String date,
        String time,
        ExpenseCategory category,
        ExpensePaymentSource paymentSource,
        String receiptImageUri,
        long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
