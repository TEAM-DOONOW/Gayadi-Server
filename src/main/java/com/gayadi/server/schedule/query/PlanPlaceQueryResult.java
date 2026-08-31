package com.gayadi.server.schedule.query;

/** 여행 일정과 계획 Repository의 PlanPlaceQueryResult 조회 결과를 전달합니다. */
public record PlanPlaceQueryResult(
        long id,
        String name,
        String category,
        String address,
        Double latitude,
        Double longitude
) {
}
