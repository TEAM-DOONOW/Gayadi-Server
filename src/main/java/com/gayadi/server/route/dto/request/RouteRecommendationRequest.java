package com.gayadi.server.route.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/** 경로 추천 생성에 필요한 경로 유형과 사용자 범위를 전달합니다. */
@Schema(name = "RouteRecommendationRequest", description = "경로 추천 요청")
public record RouteRecommendationRequest(
        @NotBlank
        @Schema(description = "경로 유형", example = "DEPARTURE", requiredMode = Schema.RequiredMode.REQUIRED)
        String type,

        @Positive
        @Schema(description = "개인 경로를 요청할 사용자 ID", example = "1", nullable = true)
        Long userId
) {
}
