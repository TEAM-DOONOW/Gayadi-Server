package com.gayadi.server.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI Agent가 관광 후보 중 선택한 장소")
public record RecommendedPlace(
        @Schema(description = "Gayadi 내부 장소 ID", example = "42") String placeId,
        @Schema(description = "장소명", example = "국립중앙박물관") String name,
        @Schema(description = "정규화된 장소 분류", example = "CULTURE") String category,
        @Schema(description = "추천 점수(0~1)", minimum = "0", maximum = "1", example = "0.91") double score,
        @Schema(description = "추천 이유") String reason,
        @Schema(description = "TourAPI 원본 콘텐츠 ID", example = "126508") String sourcePlaceId
) {
    public RecommendedPlace {
        if (placeId == null) placeId = "";
        if (name == null) name = "";
        if (category == null) category = "";
        if (reason == null) reason = "";
        if (sourcePlaceId == null) sourcePlaceId = "";
    }
}
