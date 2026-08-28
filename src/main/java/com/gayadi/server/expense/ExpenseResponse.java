package com.gayadi.server.expense;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

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
