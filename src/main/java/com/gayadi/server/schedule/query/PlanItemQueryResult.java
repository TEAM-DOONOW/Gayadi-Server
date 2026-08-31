package com.gayadi.server.schedule.query;

import java.time.LocalDateTime;

/** 여행 일정과 계획 Repository의 PlanItemQueryResult 조회 결과를 전달합니다. */
public record PlanItemQueryResult(
        long planId,
        long id,
        int sequenceNo,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        String status,
        String itemType,
        String title,
        String description,
        Integer estimatedCost,
        String memo,
        Long placeId,
        String placeName,
        String category,
        String address,
        Double latitude,
        Double longitude
) {
}
