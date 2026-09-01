package com.gayadi.server.survey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** SurveySubmissionResponse API 응답 데이터를 반환합니다. */
@Schema(name = "SurveySubmissionResponse", description = "여행 성향 답변을 채점한 결과")
public record SurveySubmissionResponse(
        long attemptId,
        Long tripId,
        String resultCode,
        int preparationScore,
        int placeScore,
        int energyScore,
        PersonalityResultResponse result
) {
}
