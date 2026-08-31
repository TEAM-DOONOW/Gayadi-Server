package com.gayadi.server.weather.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 기상청 예보 파일 버전 목록을 반환합니다. */
/** 기상청 예보 파일 버전 목록을 반환합니다. */
@Schema(name = "ForecastVersionResponse", description = "기상청 예보 파일 버전 목록")
public record ForecastVersionResponse(
        List<ForecastVersionItemResponse> items
) {
}
