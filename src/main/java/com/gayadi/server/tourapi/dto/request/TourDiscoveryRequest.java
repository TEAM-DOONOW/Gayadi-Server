package com.gayadi.server.tourapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 앱 지역의 관광정보와 혼잡도를 통합 조회하는 조건을 전달합니다. */
/** 관광지 탐색과 혼잡도 조회 조건을 전달합니다. */
@Schema(name = "TourDiscoveryRequest", description = "관광지 탐색과 혼잡도 조회 조건")
public record TourDiscoveryRequest(
        int pageSize,
        String regionName,
        LocalDate targetDate,
        String contentTypeId,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3
) {
}
