package com.gayadi.server.weather.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 기상 예보 파일 버전 조회 조건을 전달합니다. */
/** 기상청 예보 파일 버전 조회 조건을 전달합니다. */
@Schema(name = "ForecastVersionRequest", description = "기상청 예보 파일 버전 조회 조건")
public record ForecastVersionRequest(
        String fileType,
        String baseDateTime
) {
}
