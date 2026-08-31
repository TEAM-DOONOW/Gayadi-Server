package com.gayadi.server.weather.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 한 예보 시각의 기상 카테고리 값을 반환합니다. */
/** 한 예보 시각의 기상 요소를 반환합니다. */
@Schema(name = "WeatherForecastSlotResponse", description = "한 예보 시각의 기상 요소")
public record WeatherForecastSlotResponse(
        String fcstDate,
        String fcstTime,
        String temperature,
        String hourlyPrecipitation,
        String sky,
        String skyName,
        String eastWestWind,
        String northSouthWind,
        String humidity,
        String precipitationType,
        String precipitationTypeName,
        String precipitationProbability,
        String lightning,
        String windDirection,
        String windDirectionName,
        String windSpeed,
        String windSpeedName,
        String snow,
        String minTemperature,
        String maxTemperature,
        String waveHeight
) {
}
