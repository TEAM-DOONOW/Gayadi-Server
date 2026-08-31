package com.gayadi.server.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** PlanDayResponse API 응답 데이터를 반환합니다. */
@Schema(name = "PlanDayResponse", description = "하루 단위 자동 일정")
public record PlanDayResponse(
        long id,
        long trip_id,
        LocalDate plan_date,
        int day_number,
        String title,
        String description,
        String source_type,
        String status,
        String preference_snapshot,
        long created_by,
        int version,
        LocalDateTime created_at,
        LocalDateTime updated_at,
        List<PlanItemResponse> items
) {
}
