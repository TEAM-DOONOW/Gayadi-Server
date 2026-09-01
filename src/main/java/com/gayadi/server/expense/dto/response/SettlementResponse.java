package com.gayadi.server.expense.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** SettlementResponse API 응답 데이터를 반환합니다. */
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
