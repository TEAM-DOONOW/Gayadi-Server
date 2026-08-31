package com.gayadi.server.route.dto.response;

import com.gayadi.server.common.Location;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 저장된 경로 공급자와 계산 전략의 상세 데이터를 반환합니다. */
/** 경로 공급자가 계산한 원본 경로 데이터를 반환합니다. */
@Schema(name = "RouteDataResponse", description = "경로 공급자가 계산한 상세 경로 데이터")
public record RouteDataResponse(
        String provider,
        String configuredProvider,
        Boolean fallback,
        String optionId,
        String optionName,
        String strategy,
        String summary,
        Location origin,
        Location destination,
        List<Location> stops,
        List<RouteSegmentResponse> segments
) {
}
