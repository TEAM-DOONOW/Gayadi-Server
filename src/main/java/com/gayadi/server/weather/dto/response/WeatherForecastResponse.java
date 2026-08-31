package com.gayadi.server.weather.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 발표시각과 격자별 예보 전체 결과를 반환합니다. */
@Schema(name = "WeatherForecastResponse", description = "발표시각과 격자별 예보 전체 결과")
public record WeatherForecastResponse(
        String baseDate,
        String baseTime,
        int nx,
        int ny,
        List<WeatherForecastSlotResponse> forecast
) {
}
