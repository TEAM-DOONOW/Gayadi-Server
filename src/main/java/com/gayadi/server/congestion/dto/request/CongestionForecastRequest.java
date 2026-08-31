package com.gayadi.server.congestion.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 혼잡도 예측에 필요한 지역·장소·기준 시각을 전달합니다. */
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
