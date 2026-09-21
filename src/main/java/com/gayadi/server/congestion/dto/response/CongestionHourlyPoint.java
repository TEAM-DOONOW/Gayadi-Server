package com.gayadi.server.congestion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 하루 중 한 시간대의 혼잡도 예측값을 반환합니다. */
@Schema(name = "CongestionHourlyPoint", description = "한 시간대의 혼잡도 예측값")
public record CongestionHourlyPoint(
        @Schema(description = "시간(0-23)", example = "13")
        int hour,
        @Schema(description = "혼잡 점수(0-100)", example = "70")
        int concentrationScore,
        @Schema(description = "혼잡 단계", example = "CROWDED", allowableValues = {"RELAXED", "NORMAL", "CROWDED"})
        String level
) {
}
