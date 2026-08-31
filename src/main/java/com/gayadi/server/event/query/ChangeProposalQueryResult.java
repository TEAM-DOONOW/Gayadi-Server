package com.gayadi.server.event.query;

import java.time.LocalDateTime;
import java.util.List;

/** Repository가 조회하고 JSON snapshot을 복원한 일정 변경 제안 결과입니다. */
public record ChangeProposalQueryResult(
        long id,
        Long tripId,
        Long planId,
        Long eventId,
        String type,
        String reason,
        String status,
        Integer baseRevisionNo,
        List<ChangeProposalOptionQueryResult> options,
        String selectedOptionKey,
        Long decidedBy,
        LocalDateTime generatedAt,
        LocalDateTime decidedAt,
        LocalDateTime appliedAt,
        Object before,
        Object after
) {
}
