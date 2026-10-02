package com.gayadi.server.congestion.dto.response;

import com.gayadi.server.place.dto.response.PlaceResponse;
import com.gayadi.server.weather.dto.response.PlaceWeatherSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** 장소별 혼잡도 화면에 필요한 장소·날씨·혼잡도 정보를 한 번에 반환합니다. */
@Schema(name = "PlaceCongestionDetailResponse", description = "장소별 혼잡도 상세 정보")
public record PlaceCongestionDetailResponse(
        @Schema(description = "장소 기본 정보", requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceResponse place,

        @Schema(description = "현재 날씨 요약", requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceWeatherSummaryResponse weather,

        @Schema(description = "현재 및 시간대별 혼잡도", requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceCongestionSummaryResponse congestion
) {
}
