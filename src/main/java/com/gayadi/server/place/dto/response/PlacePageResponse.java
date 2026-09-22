package com.gayadi.server.place.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import com.gayadi.server.place.model.PlaceSort;

import java.util.List;

/** PlacePageResponse API 응답 데이터를 반환합니다. */
@Schema(name = "PlacePageResponse", description = "공개 장소 검색 및 이동시간순 후보 목록")
public record PlacePageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceResponse> items,

        @Schema(description = "다음 페이지 기준 ID", nullable = true)
        Long nextCursor,

        @Schema(description = "다음 페이지 존재 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean hasNext,

        @Schema(description = "실제 적용한 정렬과 후보 제한 정보")
        PlaceSearchRankingResponse ranking
) {
    public PlacePageResponse(List<PlaceResponse> items, Long nextCursor, boolean hasNext) {
        this(items, nextCursor, hasNext, new PlaceSearchRankingResponse(
                PlaceSort.RECENT, 0, false));
    }
}
