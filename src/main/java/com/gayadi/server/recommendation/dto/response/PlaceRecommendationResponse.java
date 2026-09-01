package com.gayadi.server.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 추천 장소 목록과 추천 판단 근거를 반환합니다. */
@Schema(description = "맞춤 장소 추천 Agent 응답")
public record PlaceRecommendationResponse(
        @Schema(description = "추천 장소 목록")
        List<RecommendedPlace> recommendations,

        @Schema(description = "추천 전체 판단 근거")
        String reasoning
) {
    public PlaceRecommendationResponse {
        if (recommendations == null) {
            recommendations = List.of();
        }
        if (reasoning == null) {
            reasoning = "";
        }
    }
}
