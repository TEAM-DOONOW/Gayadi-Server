package com.gayadi.server.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 대시보드에 표시할 여행의 하루 정보를 반환합니다. */
@Schema(name = "TripDayResponse", description = "여행 일자")
public record TripDayResponse(
        @Schema(description = "여행 일차", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        int dayNumber,

        @Schema(description = "날짜", example = "2026-09-01", requiredMode = Schema.RequiredMode.REQUIRED)
        String date,

        @Schema(description = "화면 표시용 날짜", example = "9.1/화", requiredMode = Schema.RequiredMode.REQUIRED)
        String dateLabel
) {
}
