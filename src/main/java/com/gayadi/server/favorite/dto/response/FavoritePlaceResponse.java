package com.gayadi.server.favorite.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gayadi.server.place.model.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** FavoritePlaceResponse API 응답 데이터를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "FavoritePlaceResponse", description = "사용자가 찜한 장소")
public record FavoritePlaceResponse(
        @Schema(description = "장소 ID", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "장소명", example = "경복궁", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "장소 카테고리", example = "ATTRACTION", requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceCategory category,

        @Schema(description = "지번 주소", example = "서울특별시 종로구 세종로 1-91", nullable = true)
        String address,

        @Schema(description = "도로명 주소", example = "서울특별시 종로구 사직로 161", nullable = true)
        String roadAddress,

        @Schema(description = "위도", example = "37.5796", requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,

        @Schema(description = "경도", example = "126.9770", requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude,

        @Schema(description = "지역 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        Long regionId,

        @Schema(description = "전화번호", example = "02-3700-3900", nullable = true)
        String phone,

        @Schema(description = "홈페이지 URL", example = "https://www.royalpalace.go.kr", nullable = true)
        String homepageUrl,

        @Schema(description = "대표 이미지 URL", example = "https://example.com/gyeongbokgung.jpg", nullable = true)
        String imageUrl,

        @Schema(description = "실내 장소 여부", example = "false", nullable = true)
        Boolean indoor,

        @Schema(description = "장소 설명", example = "조선 왕조의 법궁입니다.", nullable = true)
        String description,

        @Schema(description = "사용자가 남긴 메모", example = "오전 관람 예정", nullable = true)
        String memo,

        @Schema(description = "찜한 시각", example = "2026-08-31T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime favoritedAt
) {
}
