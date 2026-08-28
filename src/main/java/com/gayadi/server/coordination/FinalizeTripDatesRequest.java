package com.gayadi.server.coordination;

import com.gayadi.server.common.AppDateFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record FinalizeTripDatesRequest(
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "시작일은 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        String startDate,
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "종료일은 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        String endDate
) {
}
