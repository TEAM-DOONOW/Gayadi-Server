package com.gayadi.server.dashboard.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 여행 일정의 전체 개수와 방문 진행률을 반환합니다. */
@Schema(name = "DashboardProgressResponse", description = "여행 일정 진행률")
public record DashboardProgressResponse(
        @Schema(description = "전체 일정 수", example = "8", requiredMode = Schema.RequiredMode.REQUIRED)
        int scheduleCount,

        @Schema(description = "방문 완료 일정 수", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        long visitedCount,

        @Schema(description = "방문 완료 비율", example = "37", requiredMode = Schema.RequiredMode.REQUIRED)
        int percentage
) {
}
