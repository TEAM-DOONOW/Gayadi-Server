package com.gayadi.server.congestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 관광지 혼잡도 예측값과 데이터 출처·신뢰도를 반환합니다. */
/** 관광지 집중률 기반 혼잡도 예측 결과를 반환합니다. */
@Schema(name = "CongestionForecastResponse", description = "관광지 집중률 기반 혼잡도 예측 결과")
public record CongestionForecastResponse(
        String level,
        int concentrationScore,
        String area,
        String placeName,
        LocalDate targetDate,
        String source,
        boolean estimated,
        boolean providerDataAvailable,
        String confidence,
        String message
) {
}
