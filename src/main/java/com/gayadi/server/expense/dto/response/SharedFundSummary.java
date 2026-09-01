package com.gayadi.server.expense.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** SharedFundSummary API 응답 데이터를 반환합니다. */
@Schema(name = "SharedFundSummary", description = "여행 공동 경비 잔액")
public record SharedFundSummary(
        long tripId,
        long contributedAmount,
        long spentAmount,
        long balance
) {
}
