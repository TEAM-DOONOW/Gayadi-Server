package com.gayadi.server.coordination.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gayadi.server.common.AppDateFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** FinalizeTripDatesRequest API 요청 데이터를 전달합니다. */
/** 최종 확정할 여행 날짜를 전달합니다. */
@Schema(name = "FinalizeTripDatesRequest", description = "최종 확정할 여행 날짜")
public record FinalizeTripDatesRequest(
        @NotBlank(message = "{validation.coordination.start-date.required}")
        @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.coordination.start-date.pattern}")
        String startDate,

        @NotBlank(message = "{validation.coordination.end-date.required}")
        @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.coordination.end-date.pattern}")
        String endDate
) {
}
