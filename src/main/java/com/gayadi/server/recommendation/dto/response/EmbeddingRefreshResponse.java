package com.gayadi.server.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 장소 임베딩 갱신 결과와 처리 건수를 반환합니다. */
@Schema(description = "장소 임베딩 갱신 결과")
public record EmbeddingRefreshResponse(
        @Schema(example = "완료", requiredMode = Schema.RequiredMode.REQUIRED)
        String status,

        @Schema(example = "120", requiredMode = Schema.RequiredMode.REQUIRED)
        int embeddedCount,

        @Schema(example = "장소 임베딩이 완료되었습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
