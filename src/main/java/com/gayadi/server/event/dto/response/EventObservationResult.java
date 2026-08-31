package com.gayadi.server.event.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 현장 상황 등록 후 반환할 영향도 또는 일정 변경 제안 결과의 공통 타입입니다. */
@Schema(name = "EventObservationResult", description = "상황 관측 또는 일정 변경 제안 결과")
public sealed interface EventObservationResult
        permits EventObservationResponse, ChangeProposalResponse {
}
