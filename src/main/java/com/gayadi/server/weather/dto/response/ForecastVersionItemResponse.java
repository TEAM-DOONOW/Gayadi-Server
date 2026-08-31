package com.gayadi.server.weather.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 기상 예보 파일 종류와 버전을 반환합니다. */
/** 기상청 예보 파일 하나의 종류와 버전을 반환합니다. */
@Schema(name = "ForecastVersionItemResponse", description = "기상청 예보 파일 종류와 버전")
public record ForecastVersionItemResponse(
        String fileType,
        String version
) {
}
