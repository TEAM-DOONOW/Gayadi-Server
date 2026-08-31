package com.gayadi.server.expense.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.expense.model.ExpenseCategory;
import com.gayadi.server.expense.model.ExpensePaymentSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** ExpenseRequest API 요청 데이터를 전달합니다. */
/** 등록하거나 수정할 여행 경비 정보를 전달합니다. */
@Schema(name = "ExpenseRequest", description = "여행 경비 등록·수정 정보")
public record ExpenseRequest(
        Long scheduleId,

        @NotBlank(message = "{validation.expense.title.required}")
        @Size(max = 100, message = "{validation.expense.title.size}")
        String title,

        @Size(max = 1000, message = "{validation.expense.memo.size}")
        String memo,

        @Min(value = 1, message = "{validation.expense.amount.min}")
        @Max(value = 1_000_000_000_000L, message = "{validation.expense.amount.max}")
        long amount,

        Long payerId,

        @NotEmpty(message = "{validation.expense.participants.required}")
        @Size(max = 100, message = "{validation.expense.participants.size}")
        List<@NotNull(message = "{validation.expense.participant.required}") Long> participantIds,

        @NotBlank(message = "{validation.expense.date.required}")
        @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.expense.date.pattern}")
        String date,

        @NotBlank(message = "{validation.expense.time.required}")
        @Pattern(regexp = AppDateFormat.TIME_PATTERN, message = "{validation.expense.time.pattern}")
        String time,

        @NotNull(message = "{validation.expense.category.required}")
        ExpenseCategory category,

        @NotNull(message = "{validation.expense.payment-source.required}")
        ExpensePaymentSource paymentSource,

        @Size(max = 1000, message = "{validation.expense.receipt-image-uri.size}")
        String receiptImageUri
) {
}
