package com.gayadi.server.expense;

import com.gayadi.server.expense.dto.response.ExpenseResponse;
import com.gayadi.server.expense.dto.response.SettlementResponse;
import com.gayadi.server.expense.model.ExpenseCategory;
import com.gayadi.server.expense.model.ExpensePaymentSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseSettlementCalculatorTest {

    @Test
    void distributesRemainderByParticipantIdAndProducesDeterministicTransfers() {
        SettlementResponse result = ExpenseSettlementCalculator.calculate(List.of(
                expense(1, 10_001, 30L, List.of(30L, 10L), ExpensePaymentSource.PERSONAL),
                expense(2, 9_000, 20L, List.of(10L, 20L, 30L), ExpensePaymentSource.PERSONAL)
        ), List.of(30L, 20L, 10L));

        assertThat(result.totalAmount()).isEqualTo(19_001);
        assertThat(result.balances()).containsExactly(
                new SettlementResponse.ParticipantBalance(10L, 0, 8_001, -8_001),
                new SettlementResponse.ParticipantBalance(20L, 9_000, 3_000, 6_000),
                new SettlementResponse.ParticipantBalance(30L, 10_001, 8_000, 2_001));
        assertThat(result.transfers()).containsExactly(
                new SettlementResponse.Transfer(10L, 20L, 6_000),
                new SettlementResponse.Transfer(10L, 30L, 2_001));
    }

    @Test
    void sharedFundExpenseDoesNotCreateDebtBetweenParticipants() {
        SettlementResponse result = ExpenseSettlementCalculator.calculate(List.of(
                expense(1, 10_001, null, List.of(20L, 10L), ExpensePaymentSource.SHARED_FUND)
        ), List.of(10L, 20L));

        assertThat(result.balances()).containsExactly(
                new SettlementResponse.ParticipantBalance(10L, 5_001, 5_001, 0),
                new SettlementResponse.ParticipantBalance(20L, 5_000, 5_000, 0));
        assertThat(result.transfers()).isEmpty();
    }

    private ExpenseResponse expense(
            long id,
            long amount,
            Long payerId,
            List<Long> participantIds,
            ExpensePaymentSource source) {
        return new ExpenseResponse(
                id, 1, null, "지출", "", amount, payerId, participantIds,
                "2026.08.25", "12:00", ExpenseCategory.OTHER, source,
                null, 10, LocalDateTime.MIN, LocalDateTime.MIN);
    }
}
