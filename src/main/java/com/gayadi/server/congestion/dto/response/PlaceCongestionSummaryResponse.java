package com.gayadi.server.congestion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** 장소 상세 화면에 표시할 현재 및 시간대별 혼잡도를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PlaceCongestionSummaryResponse", description = "장소의 현재 및 시간대별 혼잡도")
public record PlaceCongestionSummaryResponse(
        @Schema(description = "혼잡도 데이터 제공 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean available,

        @Schema(description = "현재 또는 기준 혼잡 단계", example = "CROWDED", requiredMode = Schema.RequiredMode.REQUIRED)
        String currentLevel,

        @Schema(description = "0~100 범위의 혼잡 예측 점수", example = "77", nullable = true)
        Integer currentScore,

        @Schema(description = "현재 1제곱미터당 추정 방문자 수", nullable = true)
        Double currentDensityPerSquareMeter,

        @Schema(description = "현재 추정 인구 최솟값", nullable = true)
        Integer currentPopulationMin,

        @Schema(description = "현재 추정 인구 최댓값", nullable = true)
        Integer currentPopulationMax,

        @Schema(description = "REALTIME 또는 FORECAST", example = "REALTIME", requiredMode = Schema.RequiredMode.REQUIRED)
        String dataType,

        @Schema(description = "데이터 제공기관 또는 추정 방식", requiredMode = Schema.RequiredMode.REQUIRED)
        String source,

        @Schema(description = "실시간 혼잡도 관측 시각", nullable = true)
        OffsetDateTime observedAt,

        @Schema(description = "시간대별 혼잡도의 기준일", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate targetDate,

        @Schema(description = "시간대별 값의 산정 방식", example = "RECENT_30_DAY_SAME_WEEKDAY_STATISTICS",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String hourlyDataType,

        @Schema(description = "시간대별 데이터 제공기관 또는 추정 방식", requiredMode = Schema.RequiredMode.REQUIRED)
        String hourlySource,

        @Schema(description = "혼잡도 데이터 안내 문구", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,

        @Schema(description = "시간대별 혼잡도", requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceHourlyCongestionResponse> hourly
) {
}
