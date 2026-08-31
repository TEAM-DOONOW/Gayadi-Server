package com.gayadi.server.survey.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** GroupPersonalityResponse API 응답 데이터를 반환합니다. */
@Schema(name = "GroupPersonalityResponse", description = "여행 참여자의 성향 분포")
public record GroupPersonalityResponse(
        String dominantProfile,

        long responseCount,

        @Schema(description = "성향 코드별 제출 인원")
        Map<String, Long> distribution
) {
}
