package com.gayadi.server.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** TripResponse API 응답 데이터를 반환합니다. */
@Schema(name = "TripResponse", description = "여행 기본 정보")
public record TripResponse(
        @Schema(example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(example = "제주도 우정 여행", requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(example = "2026.08.20", requiredMode = Schema.RequiredMode.REQUIRED)
        String startDate,

        @Schema(example = "2026.08.22", requiredMode = Schema.RequiredMode.REQUIRED)
        String endDate,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> cities,

        @Schema(example = "PLANNING", requiredMode = Schema.RequiredMode.REQUIRED)
        String status,

        @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        long ownerId,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Long> participantIds,

        @Schema(example = "U7K9P2", requiredMode = Schema.RequiredMode.REQUIRED)
        String inviteCode,

        @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int version,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime updatedAt
) {
}
