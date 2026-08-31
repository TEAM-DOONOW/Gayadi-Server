package com.gayadi.server.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 사용자에게 선택 가능한 일정 변경 장소 후보를 반환합니다. */
/** 일정 변경 제안의 선택 가능한 대안을 반환합니다. */
@Schema(name = "ChangeProposalOptionResponse", description = "일정 변경 제안 선택지")
public record ChangeProposalOptionResponse(
        String key,
        long placeId,
        String placeName,
        String description,
        boolean requireIndoor
) {
}
