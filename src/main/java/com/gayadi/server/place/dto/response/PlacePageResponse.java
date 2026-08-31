package com.gayadi.server.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** PlacePageResponse API 응답 데이터를 반환합니다. */
@Schema(name = "PlacePageResponse", description = "커서 기반 공개 장소 목록")
public record PlacePageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceResponse> items,

        @Schema(description = "다음 페이지 기준 ID", nullable = true)
        Long nextCursor,

        @Schema(description = "다음 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext
) {
}
