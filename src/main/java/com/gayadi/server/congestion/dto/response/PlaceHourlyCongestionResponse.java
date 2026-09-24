package com.gayadi.server.congestion.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 장소 상세 화면의 한 시간대 혼잡도 통계 또는 예측값을 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PlaceHourlyCongestionResponse", description = "한 시간대의 장소 혼잡도")
public record PlaceHourlyCongestionResponse(
        @Schema(description = "시간(0~23)", example = "13", requiredMode = Schema.RequiredMode.REQUIRED)
        int hour,

        @Schema(description = "0~100 범위의 표시용 상대 점수", example = "88", nullable = true)
        Integer score,

        @Schema(description = "1제곱미터당 추정 방문자 수", example = "0.03126", nullable = true)
        Double densityPerSquareMeter,

        @Schema(description = "예상 인구 최솟값", example = "6500", nullable = true)
        Integer populationMin,

        @Schema(description = "예상 인구 최댓값", example = "7000", nullable = true)
        Integer populationMax,

        @Schema(description = "혼잡 단계", example = "CROWDED", requiredMode = Schema.RequiredMode.REQUIRED)
        String level
) {
}
