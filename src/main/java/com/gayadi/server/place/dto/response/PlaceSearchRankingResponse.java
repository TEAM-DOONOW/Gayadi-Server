package com.gayadi.server.place.dto.response;

import com.gayadi.server.place.model.PlaceSort;
import io.swagger.v3.oas.annotations.media.Schema;

/** 제한된 후보 내 정렬임을 명시하며 ID 커서와 구별합니다. */
public record PlaceSearchRankingResponse(
        PlaceSort sort,
        @Schema(description = "이동시간 비교를 수행한 후보 수") int evaluatedCandidates,
        @Schema(description = "반환 개수 또는 근접 후보 제한으로 검색 결과 일부만 제공했는지 여부") boolean limited
) {
}
