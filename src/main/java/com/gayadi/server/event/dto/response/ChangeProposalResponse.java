package com.gayadi.server.event.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 일정 변경 제안의 상태, 선택지와 적용 전후 정보를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ChangeProposalResponse", description = "현장 상황으로 생성된 일정 변경 제안")
public record ChangeProposalResponse(
        long id,
        Long tripId,
        Long planId,
        Long eventId,
        String type,
        String reason,
        String status,
        Integer baseRevisionNo,
        List<ChangeProposalOptionResponse> options,
        String selectedOptionKey,
        Long decidedBy,
        LocalDateTime generatedAt,
        LocalDateTime decidedAt,
        LocalDateTime appliedAt,
        Object before,
        Object after
) implements EventObservationResult {
}
