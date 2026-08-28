package com.gayadi.server.expense;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "ExpenseSettlementResponse", description = "원 단위로 정확히 계산한 여행 정산")
public record SettlementResponse(
        long totalAmount,
        List<ParticipantBalance> balances,
        List<Transfer> transfers
) {
    public record ParticipantBalance(
            long participantId,
            long paidAmount,
            long owedAmount,
            long netAmount
    ) {
    }

    public record Transfer(
            long fromParticipantId,
            long toParticipantId,
            long amount
    ) {
    }
}
