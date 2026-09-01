package com.gayadi.server.route.query;

import com.gayadi.server.route.RoutePhase;

import java.time.LocalDateTime;

/** 저장된 추천·선택 경로의 상세 조회 결과를 전달합니다. */
public record RouteQueryResult(
        long id,
        long planId,
        long tripId,
        Long memberId,
        Long memberUserId,
        RoutePhase phase,
        String routeData,
        String transportMode,
        Integer durationMinutes,
        Integer distanceMeters,
        Integer transferCount,
        Integer fare,
        String status,
        LocalDateTime recommendedAt,
        LocalDateTime selectedAt
) {
}
