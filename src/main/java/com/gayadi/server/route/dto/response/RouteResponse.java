package com.gayadi.server.route.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gayadi.server.common.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 추천하거나 선택한 여행 이동 경로를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "RouteResponse", description = "추천하거나 선택한 이동 경로")
public record RouteResponse(
        long id,
        String optionId,
        String name,
        long tripId,
        long planId,
        Long memberId,
        Long userId,
        Long participantId,
        String scope,
        String type,
        String phase,
        Location origin,
        Location destination,
        List<Location> stops,
        List<RouteSegmentResponse> segments,
        Integer durationMinutes,
        Integer distanceMeters,
        Integer transferCount,
        Integer fare,
        String transportMode,
        String status,
        String provider,
        String configuredProvider,
        Boolean fallback,
        String summary,
        LocalDateTime recommendedAt,
        LocalDateTime selectedAt,
        RouteDataResponse routeData,
        List<RouteResponse> options
) {
}
