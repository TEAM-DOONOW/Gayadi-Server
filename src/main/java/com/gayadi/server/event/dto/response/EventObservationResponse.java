package com.gayadi.server.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 일정 변경이 필요하지 않은 현장 상황 등록 결과를 반환합니다. */
@Schema(name = "EventObservationResponse", description = "일정 변경이 필요하지 않은 현장 상황 등록 결과")
public record EventObservationResponse(
        long eventId,
        boolean impact,
        String message
) implements EventObservationResult {
}
