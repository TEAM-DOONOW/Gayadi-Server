package com.gayadi.server.route.query;

/** 경로 계산에 사용하는 장소 좌표 조회 결과를 전달합니다. */
public record RoutePlaceQueryResult(
        long id,
        String name,
        double latitude,
        double longitude
) {
}
