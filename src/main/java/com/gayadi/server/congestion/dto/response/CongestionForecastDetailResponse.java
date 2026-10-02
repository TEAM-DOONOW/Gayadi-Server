package com.gayadi.server.congestion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gayadi.server.weather.dto.response.PlaceWeatherSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 일별 혼잡 예상, 시간대 분포, 선택적 현재 날씨를 한 응답으로 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "CongestionForecastDetailResponse", description = "일별 혼잡 예상과 시간대 분포, 좌표가 있으면 현재 날씨")
public record CongestionForecastDetailResponse(
        @Schema(description = "기준 혼잡 단계", example = "CROWDED",
                allowableValues = {"RELAXED", "NORMAL", "CROWDED"})
        String level,

        @Schema(description = "0~100 일별 집중률", example = "75")
        int concentrationScore,

        @Schema(description = "지역 표시명", example = "서울 종로구")
        String area,

        @Schema(description = "장소 표시명", example = "경복궁")
        String placeName,

        @Schema(description = "예측 기준일", example = "2026-09-01")
        LocalDate targetDate,

        @Schema(description = "일별 점수 출처", example = "KTO_DISTRICT_CONCENTRATION_FORECAST")
        String source,

        @Schema(description = "실시간 인원수가 아닌 예측·추정값인지")
        boolean estimated,

        @Schema(description = "공공 예측 자료를 사용했는지")
        boolean providerDataAvailable,

        @Schema(description = "일별 점수 신뢰도", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        String confidence,

        @Schema(description = "일별 점수 안내")
        String message,

        @Schema(description = "시간대 목록이 쓰는 기준 단계. level과 같습니다.", example = "CROWDED")
        String baseLevel,

        @Schema(description = "시간대 목록이 쓰는 기준 점수. concentrationScore와 같습니다.", example = "75")
        int baseScore,

        @Schema(description = "시간대별 혼잡 예상. 제공기관 자료가 일별이라 항상 추정치입니다.")
        List<CongestionHourlyPoint> points,

        @Schema(description = "lat, lon을 보낸 경우의 현재 날씨. 좌표가 없으면 생략합니다.")
        PlaceWeatherSummaryResponse weather
) {

    public static CongestionForecastDetailResponse of(
            CongestionForecastResponse daily,
            CongestionHourlyForecastResponse hourly,
            PlaceWeatherSummaryResponse weather) {
        return new CongestionForecastDetailResponse(
                daily.level(),
                daily.concentrationScore(),
                daily.area(),
                daily.placeName(),
                daily.targetDate(),
                daily.source(),
                daily.estimated(),
                daily.providerDataAvailable(),
                daily.confidence(),
                daily.message(),
                daily.level(),
                daily.concentrationScore(),
                hourly.points(),
                weather);
    }
}
