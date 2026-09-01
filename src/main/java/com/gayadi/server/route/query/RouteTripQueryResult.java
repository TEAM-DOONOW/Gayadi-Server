package com.gayadi.server.route.query;

import com.gayadi.server.travel.model.DepartureMode;

/** 경로 계산에 필요한 여행 출발 설정을 전달합니다. */
public record RouteTripQueryResult(
        long id,
        DepartureMode departureMode,
        Long meetingPlaceId
) {
}
