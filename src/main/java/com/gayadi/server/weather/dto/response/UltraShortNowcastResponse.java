package com.gayadi.server.weather.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 기상청 초단기실황 관측값을 반환합니다. */
@Schema(name = "UltraShortNowcastResponse", description = "기상청 초단기실황 관측값")
public record UltraShortNowcastResponse(
        String baseDate,
        String baseTime,
        int nx,
        int ny,
        String temperature,
        String hourlyPrecipitationRaw,
        String hourlyPrecipitation,
        String eastWestWind,
        String northSouthWind,
        String humidity,
        String precipitationType,
        String precipitationTypeName,
        String windDirection,
        String windDirectionName,
        String windSpeed,
        String windSpeedName
) {
}
