package com.gayadi.server.expense;

import com.gayadi.server.common.AppDateFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExpenseRequest(
        Long scheduleId,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 1000) String memo,
        @Min(1) @Max(1_000_000_000_000L) long amount,
        Long payerId,
        @NotEmpty @Size(max = 100) List<@NotNull Long> participantIds,
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "지출 날짜는 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        String date,
        @NotBlank
        @Pattern(regexp = AppDateFormat.TIME_PATTERN,
                message = "지출 시각은 HH:mm 형식이어야 합니다.")
        String time,
        @NotNull ExpenseCategory category,
        @NotNull ExpensePaymentSource paymentSource,
        @Size(max = 1000) String receiptImageUri
) {
}
