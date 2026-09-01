package com.gayadi.server.schedule.dto.response;

import com.gayadi.server.schedule.model.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;

/** ScheduleResponse API 응답 데이터를 반환합니다. */
@Schema(name = "ScheduleResponse", description = "여행 일정 항목")
public record ScheduleResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        long tripId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(nullable = true)
        Long placeId,

        @Schema(nullable = true)
        String placeName,

        @Schema(example = "2026.08.31", requiredMode = Schema.RequiredMode.REQUIRED)
        String date,

        @Schema(example = "10:30", requiredMode = Schema.RequiredMode.REQUIRED)
        String time,

        @Schema(example = "12:00", nullable = true)
        String endTime,

        @Schema(nullable = true)
        String memo,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ScheduleType type,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int order,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isVisited
) {
}
