package com.gayadi.server.route.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 추천 경로를 선택하기 위한 경로 번호 또는 옵션 식별자를 전달합니다. */
@Schema(name = "RouteSelectionRequest", description = "추천 경로 선택 요청")
public record RouteSelectionRequest(
        @Positive
        @Schema(description = "서버에 저장된 경로 ID", example = "10", nullable = true)
        Long routeId,

        @Size(max = 20)
        @Schema(description = "앱에서 선택한 경로 옵션 ID", example = "fast", nullable = true)
        String optionId,

        @Positive
        @Schema(description = "개인 경로를 선택할 사용자 ID", example = "1", nullable = true)
        Long userId
) {
}
