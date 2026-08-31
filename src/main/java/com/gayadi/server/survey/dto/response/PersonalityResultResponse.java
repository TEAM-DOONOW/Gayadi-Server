package com.gayadi.server.survey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** PersonalityResultResponse API 응답 데이터를 반환합니다. */
@Schema(name = "PersonalityResultResponse", description = "여행 성향 결과")
public record PersonalityResultResponse(
        @Schema(example = "PNR", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        String emoji,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,

        String characterKey,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> hashtags,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> strengths,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> weaknesses,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<CompatibleType> compatibleTypes,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        TravelRole travelRole
) {
    public record CompatibleType(
            String code,
            String emoji,
            String name
    ) {
    }

    public record TravelRole(
            String icon,
            String title,
            String description
    ) {
    }
}
