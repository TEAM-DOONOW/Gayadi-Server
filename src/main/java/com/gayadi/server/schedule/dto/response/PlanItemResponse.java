package com.gayadi.server.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** PlanItemResponse API 응답 데이터를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PlanItemResponse", description = "자동 일정의 장소 또는 활동")
public record PlanItemResponse(
        long id,
        int sequence_no,
        LocalDateTime planned_start,
        LocalDateTime planned_end,
        String status,
        String item_type,
        String title,
        String description,
        Integer estimated_cost,
        String memo,
        Long place_id,
        String place_name,
        String category,
        String address,
        Double latitude,
        Double longitude
) {
}
