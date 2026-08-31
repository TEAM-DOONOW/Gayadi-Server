package com.gayadi.server.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gayadi.server.event.dto.response.ChangeProposalOptionResponse;
import com.gayadi.server.event.dto.response.ChangeProposalResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** 상황 대응 과정에서 생성된 선택 대기 변경 제안을 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "상황 대응으로 생성된 변경 제안")
public record SituationChangeProposalResponse(
        Long id,
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
) {

    public static SituationChangeProposalResponse empty() {
        return new SituationChangeProposalResponse(
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null);
    }

    public static SituationChangeProposalResponse from(ChangeProposalResponse response) {
        return new SituationChangeProposalResponse(
                response.id(),
                response.tripId(),
                response.planId(),
                response.eventId(),
                response.type(),
                response.reason(),
                response.status(),
                response.baseRevisionNo(),
                response.options(),
                response.selectedOptionKey(),
                response.decidedBy(),
                response.generatedAt(),
                response.decidedAt(),
                response.appliedAt(),
                response.before(),
                response.after());
    }
}
