package com.gayadi.server.expense;

import com.gayadi.server.expense.dto.response.ExpenseResponse;
import com.gayadi.server.expense.dto.response.SettlementResponse;
import com.gayadi.server.expense.model.ExpensePaymentSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Android의 원 단위 분담 규칙과 동일한 결정론적 정산 계산기입니다. */
final class ExpenseSettlementCalculator {

    private ExpenseSettlementCalculator() {
    }

    static SettlementResponse calculate(List<ExpenseResponse> expenses, List<Long> memberIds) {
        Map<Long, MutableTotals> totals = new LinkedHashMap<>();
        memberIds.stream().distinct().sorted().forEach(id -> totals.put(id, new MutableTotals()));
        long totalAmount = 0;

        for (ExpenseResponse expense : expenses) {
            totalAmount = Math.addExact(totalAmount, expense.amount());
            List<Long> participantIds = expense.participantIds().stream().distinct().sorted().toList();
            long baseShare = expense.amount() / participantIds.size();
            int remainder = Math.toIntExact(expense.amount() % participantIds.size());
            for (int index = 0; index < participantIds.size(); index++) {
                MutableTotals participant = totals.computeIfAbsent(
                        participantIds.get(index), ignored -> new MutableTotals());
                long share = baseShare + (index < remainder ? 1 : 0);
                participant.owed = Math.addExact(participant.owed, share);
                if (expense.paymentSource() == ExpensePaymentSource.SHARED_FUND) {
                    participant.paid = Math.addExact(participant.paid, share);
                }
            }
            if (expense.paymentSource() == ExpensePaymentSource.PERSONAL) {
                MutableTotals payer = totals.computeIfAbsent(expense.payerId(), ignored -> new MutableTotals());
                payer.paid = Math.addExact(payer.paid, expense.amount());
            }
        }

        List<SettlementResponse.ParticipantBalance> balances = totals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new SettlementResponse.ParticipantBalance(
                        entry.getKey(), entry.getValue().paid, entry.getValue().owed,
                        entry.getValue().paid - entry.getValue().owed))
                .toList();
        return new SettlementResponse(totalAmount, balances, transfers(balances));
    }

    private static List<SettlementResponse.Transfer> transfers(
            List<SettlementResponse.ParticipantBalance> balances) {
        Comparator<Party> order = Comparator.comparingLong(Party::remaining).reversed()
                .thenComparingLong(Party::id);
        List<Party> debtors = balances.stream()
                .filter(balance -> balance.netAmount() < 0)
                .map(balance -> new Party(balance.participantId(), -balance.netAmount()))
                .sorted(order).toList();
        List<Party> creditors = balances.stream()
                .filter(balance -> balance.netAmount() > 0)
                .map(balance -> new Party(balance.participantId(), balance.netAmount()))
                .sorted(order).toList();
        List<SettlementResponse.Transfer> result = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Party debtor = debtors.get(debtorIndex);
            Party creditor = creditors.get(creditorIndex);
            long amount = Math.min(debtor.remaining, creditor.remaining);
            result.add(new SettlementResponse.Transfer(debtor.id, creditor.id, amount));
            debtor.remaining -= amount;
            creditor.remaining -= amount;
            if (debtor.remaining == 0) {
                debtorIndex++;
            }
            if (creditor.remaining == 0) {
                creditorIndex++;
            }
        }
        return List.copyOf(result);
    }

    private static final class MutableTotals {
        private long paid;
        private long owed;
    }

    private static final class Party {
        private final long id;
        private long remaining;

        private Party(long id, long remaining) {
            this.id = id;
            this.remaining = remaining;
        }

        private long id() {
            return id;
        }
        private long remaining() {
            return remaining;
        }
    }
}
