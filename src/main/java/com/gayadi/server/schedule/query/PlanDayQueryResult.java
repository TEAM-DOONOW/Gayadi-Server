package com.gayadi.server.schedule.query;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 여행 일정과 계획 Repository의 PlanDayQueryResult 조회 결과를 전달합니다. */
public record PlanDayQueryResult(
        long id,
        long tripId,
        int dayNumber,
        LocalDate planDate,
        String title,
        String description,
        String sourceType,
        String status,
        String preferenceSnapshot,
        long createdBy,
        int version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
