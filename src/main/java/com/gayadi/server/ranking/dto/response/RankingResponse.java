package com.gayadi.server.ranking.dto.response;

import com.gayadi.server.ranking.RankingType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 카테고리별 순위 목록을 반환합니다. */
@Schema(name = "RankingResponse", description = "카테고리별 순위 목록")
public record RankingResponse(
        RankingType type,
        @Schema(description = "요청 지역. 전국이면 빈 문자열", example = "서울")
        String region,
        @Schema(description = "순위 기준 기간", example = "2026-07")
        String basePeriod,
        @Schema(description = "자료 출처", example = "KTO_DATALAB")
        String source,
        @Schema(description = "제공기관 순위 자료를 사용했는지 여부. false이면 대체 목록입니다")
        boolean providerDataAvailable,
        List<RankingItemResponse> items
) {
}
