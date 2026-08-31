package com.gayadi.server.survey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** SurveyResponse API 응답 데이터를 반환합니다. */
@Schema(name = "SurveyResponse", description = "여행 성향 설문 문항과 결과 종류")
public record SurveyResponse(
        @Schema(example = "travel-personality-v1", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        String description,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        int version,

        @Schema(example = "active", requiredMode = Schema.RequiredMode.REQUIRED)
        String status,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<String> resultCodeOrder,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<Question> questions,

        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PersonalityResultResponse> results
) {
    public record Question(
            String id,
            String title,
            String dimension,
            int order,
            List<Option> options
    ) {
    }

    public record Option(
            String id,
            String text,
            String code
    ) {
    }
}
