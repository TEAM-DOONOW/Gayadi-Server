package com.gayadi.server.weather.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/** 장소 상세 화면에 표시할 현재 날씨 요약을 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PlaceWeatherSummaryResponse", description = "장소의 현재 날씨 요약")
public record PlaceWeatherSummaryResponse(
        @Schema(description = "날씨 데이터 제공 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean available,

        @Schema(description = "현재 날씨 설명", example = "맑음", nullable = true)
        String condition,

        @Schema(description = "현재 기온(섭씨)", example = "23.0", nullable = true)
        Double temperatureCelsius,

        @Schema(description = "가장 가까운 예보 시각의 강수확률", example = "10", nullable = true)
        Integer precipitationProbability,

        @Schema(description = "실황 발표 기준 시각", nullable = true)
        OffsetDateTime observedAt,

        @Schema(description = "하늘 상태와 강수확률의 예보 기준 시각", nullable = true)
        OffsetDateTime forecastAt,

        @Schema(description = "데이터 제공기관", example = "KMA", requiredMode = Schema.RequiredMode.REQUIRED)
        String source,

        @Schema(description = "데이터 안내 문구", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
