package com.gayadi.server.congestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 일별 기준 점수에 시간대 분포를 적용한 시간대별 혼잡도 예측을 반환합니다. */
@Schema(name = "CongestionHourlyForecastResponse", description = "시간대별 혼잡도 예측 결과")
public record CongestionHourlyForecastResponse(
        String area,
        String placeName,
        LocalDate targetDate,
        String baseLevel,
        int baseScore,
        String source,
        boolean estimated,
        boolean providerDataAvailable,
        String confidence,
        String message,
        List<CongestionHourlyPoint> points
) {
}
