package com.gayadi.server.ranking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 카테고리 순위의 한 항목을 반환합니다. */
@Schema(name = "RankingItemResponse", description = "카테고리 순위 항목")
public record RankingItemResponse(
        @Schema(description = "1부터 시작하는 순위", example = "1")
        int rank,
        @Schema(description = "표시 이름", example = "경복궁")
        String title,
        @Schema(description = "보조 설명(지역, 분류, 기간 등)", example = "서울 종로구 · 역사관광")
        String subtitle,
        @Schema(description = "대표 이미지 URL. 없으면 null")
        String imageUrl,
        @Schema(description = "위도. 지역 순위 등 좌표가 없으면 null")
        Double latitude,
        @Schema(description = "경도. 지역 순위 등 좌표가 없으면 null")
        Double longitude,
        @Schema(description = "가야디 장소 ID. 맛집 순위에서만 채워집니다")
        Long placeId,
        @Schema(description = "TourAPI contentId. 이미지 보강에 성공한 관광지·축제에서 채워집니다")
        String contentId,
        @Schema(description = "순위 근거 값(방문자 수, 찜 수 등). 없으면 null")
        Long metric,
        @Schema(description = "순위 근거 설명", example = "찜 12개")
        String metricLabel
) {
}
