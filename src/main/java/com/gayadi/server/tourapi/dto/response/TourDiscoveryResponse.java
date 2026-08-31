package com.gayadi.server.tourapi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** 지역 관광정보와 장소별 혼잡도 예측 목록을 반환합니다. */
@Schema(description = "Android 장소 목록과 관광지 혼잡도 예측을 합친 응답")
public record TourDiscoveryResponse(
        @Schema(description = "관광지 및 혼잡도 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        List<TourDiscoveryPlaceResponse> items,

        @Schema(description = "현재 응답 항목 수", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalCount,

        @Schema(description = "요청한 페이지 크기", example = "20", requiredMode = Schema.RequiredMode.REQUIRED)
        int pageSize,

        @Schema(description = "호환용 커서. 현재 항상 null", nullable = true)
        String nextCursor,

        @Schema(description = "요청한 앱 지역명", example = "서울", requiredMode = Schema.RequiredMode.REQUIRED)
        String regionName,

        @Schema(description = "혼잡도 예측 기준일", example = "2026-09-01", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate targetDate
) {
}
