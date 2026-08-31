package com.gayadi.server.route.dto.response;

import com.gayadi.server.common.Location;
import io.swagger.v3.oas.annotations.media.Schema;

/** 추천 경로를 구성하는 한 이동 구간을 반환합니다. */
/** 경로를 구성하는 개별 이동 구간을 반환합니다. */
@Schema(name = "RouteSegmentResponse", description = "출발지와 도착지 사이의 개별 이동 구간")
public record RouteSegmentResponse(
        int order,
        Location origin,
        Location destination,
        int durationMinutes,
        int transferCount,
        int fare,
        String summary,
        String strategySummary
) {
}
