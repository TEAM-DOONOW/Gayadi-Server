package com.gayadi.server.congestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 관광지 혼잡도 예측 조건을 전달합니다. */
@Schema(name = "CongestionForecastRequest", description = "관광지 혼잡도 예측 조건")
public record CongestionForecastRequest(
        String areaCode,
        String districtCode,
        String areaName,
        String placeName,
        String targetAt
) {
}
