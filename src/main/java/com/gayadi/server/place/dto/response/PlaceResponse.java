package com.gayadi.server.place.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gayadi.server.place.model.PlaceCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** PlaceResponse API 응답 데이터를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PlaceResponse", description = "공개 장소 정보")
public record PlaceResponse(
        @Schema(description = "장소 ID", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "장소명", example = "경복궁", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(description = "사용자 표시용 분류", example = "관광명소", requiredMode = Schema.RequiredMode.REQUIRED)
        String category,

        @Schema(description = "장소 분류 코드", example = "ATTRACTION", requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceCategory categoryCode,

        @Schema(description = "평점", example = "0.0", requiredMode = Schema.RequiredMode.REQUIRED)
        double rating,

        @Schema(description = "리뷰 수", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int reviews,

        @Schema(description = "리뷰 수 호환 필드", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int reviewCount,

        @Schema(description = "평점 데이터 제공 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean ratingAvailable,

        @Schema(description = "혼잡도", example = "NORMAL", requiredMode = Schema.RequiredMode.REQUIRED)
        String crowdLevel,

        @Schema(description = "혼잡도 데이터 제공 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean crowdDataAvailable,

        @Schema(description = "분류 이모지", example = "🏞️", requiredMode = Schema.RequiredMode.REQUIRED)
        String emoji,

        @Schema(description = "장소 설명", requiredMode = Schema.RequiredMode.REQUIRED)
        String description,

        @Schema(description = "지번 주소", nullable = true)
        String address,

        @Schema(description = "도로명 주소", nullable = true)
        String roadAddress,

        @Schema(description = "위도", requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,

        @Schema(description = "경도", requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude,

        @Schema(description = "지역 ID", requiredMode = Schema.RequiredMode.REQUIRED)
        long regionId,

        @Schema(description = "지역명", requiredMode = Schema.RequiredMode.REQUIRED)
        String regionName,

        @Schema(description = "전화번호", nullable = true)
        String phone,

        @Schema(description = "홈페이지 URL", nullable = true)
        String homepageUrl,

        @Schema(description = "대표 이미지 URL", nullable = true)
        String imageUrl,

        @Schema(description = "실내 장소 여부", nullable = true)
        Boolean indoor,

        @Schema(description = "기본 정보", nullable = true)
        String basicInfo,

        @Schema(description = "운영 시간", nullable = true)
        String operatingHours,

        @Schema(description = "수정 시각", nullable = true)
        LocalDateTime updatedAt
) {
}
