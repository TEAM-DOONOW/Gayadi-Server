package com.gayadi.server.event.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 클라이언트의 일정 변경 제안 승인·거절 요청을 전달합니다. */
/** 일정 변경 제안에 대한 참여자의 결정을 전달합니다. */
@Schema(name = "ChangeProposalDecisionRequest", description = "일정 변경 제안 결정")
public record ChangeProposalDecisionRequest(
        @NotNull(message = "{validation.event.approve.required}")
        Boolean approve,

        String selectedOptionKey,

        @NotNull(message = "{validation.event.base-revision.required}")
        @PositiveOrZero(message = "{validation.event.base-revision.positive-or-zero}")
        Integer baseRevisionNo
) {
}
