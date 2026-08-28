package com.gayadi.server.expense;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SharedFundSummary", description = "여행 공동 경비 잔액")
public record SharedFundSummary(
        long tripId,
        long contributedAmount,
        long spentAmount,
        long balance
) {
}
