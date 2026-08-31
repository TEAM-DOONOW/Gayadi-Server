package com.gayadi.server.schedule.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** PlanResponse API 응답 데이터를 반환합니다. */
@Schema(name = "PlanResponse", description = "자동으로 만든 전체 여행 일정")
public record PlanResponse(
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
        List<PlanItemResponse> items,
        List<PlanDayResponse> days
) {
}
