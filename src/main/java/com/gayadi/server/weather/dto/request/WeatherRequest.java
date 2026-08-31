package com.gayadi.server.weather.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 기상 조회에 사용할 좌표와 발표 기준 시각을 전달합니다. */
/** 기상 조회 위치와 발표 기준을 전달합니다. */
@Schema(name = "WeatherRequest", description = "날씨 조회 위치와 발표 기준")
public record WeatherRequest(
        Double lat,
        Double lon,
        Integer nx,
        Integer ny,
        String baseDate,
        String baseTime
) {
}
