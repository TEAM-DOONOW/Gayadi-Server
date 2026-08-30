package com.gayadi.server.expense;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.TripErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ExpenseService {

    private final JdbcClient jdbc;
    private final TripService trips;
    private final KeyHelper keyHelper;

    public ExpenseService(JdbcClient jdbc, TripService trips, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.keyHelper = keyHelper;
    }

    public List<ExpenseResponse> list(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        List<Map<String, Object>> rows = expenseRows(tripId);
        return responses(rows, participantIds(rows));
    }

    public SettlementResponse settlement(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        List<Long> memberIds = joinedMemberIds(tripId);
        return ExpenseSettlementCalculator.calculate(list(actorId, tripId), memberIds);
    }

    @Transactional
    public ExpenseResponse create(long actorId, long tripId, ExpenseRequest request) {
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        ValidatedExpense command = validate(tripId, trip, request, null);
        long expenseId = keyHelper.insert("""
                INSERT INTO trip_expenses
                    (trip_id, schedule_id, title, memo, amount, payer_user_id,
                     expense_date, expense_time, category, payment_source,
                     receipt_image_uri, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tripId, request.scheduleId(), request.title().trim(), normalizedMemo(request.memo()),
                request.amount(), command.payerId(), command.date(), command.time(),
                request.category().name(), request.paymentSource().name(),
                blankToNull(request.receiptImageUri()), actorId);
        replaceParticipants(expenseId, command.participantIds());
        return getResponse(tripId, expenseId);
    }

    @Transactional
    public ExpenseResponse update(
            long actorId, long tripId, long expenseId, ExpenseRequest request) {
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        lockedExpense(tripId, expenseId);
        ValidatedExpense command = validate(tripId, trip, request, expenseId);
        jdbc.sql("""
                UPDATE trip_expenses
                SET schedule_id = ?, title = ?, memo = ?, amount = ?, payer_user_id = ?,
                    expense_date = ?, expense_time = ?, category = ?, payment_source = ?,
                    receipt_image_uri = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ?
                """)
                .params(request.scheduleId(), request.title().trim(), normalizedMemo(request.memo()),
                        request.amount(), command.payerId(), command.date(), command.time(),
                        request.category().name(), request.paymentSource().name(),
                        blankToNull(request.receiptImageUri()), expenseId, tripId)
                .update();
        replaceParticipants(expenseId, command.participantIds());
        return getResponse(tripId, expenseId);
    }

    @Transactional
    public void delete(long actorId, long tripId, long expenseId) {
        lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        int deleted = jdbc.sql("DELETE FROM trip_expenses WHERE id = ? AND trip_id = ?")
                .params(expenseId, tripId)
                .update();
        if (deleted == 0) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
        }
    }

    public SharedFundSummary sharedFund(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        return sharedFundSummary(tripId);
    }

    @Transactional
    public SharedFundSummary contribute(long actorId, long tripId, long amount) {
        lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        keyHelper.insert("""
                INSERT INTO trip_shared_fund_contributions (trip_id, amount, created_by)
                VALUES (?, ?, ?)
                """, tripId, amount, actorId);
        return sharedFundSummary(tripId);
    }

    private ValidatedExpense validate(
            long tripId, Map<String, Object> trip, ExpenseRequest request, Long excludedExpenseId) {
        LocalDate date = AppDateFormat.parseDate(request.date(), "지출 날짜");
        LocalTime time = AppDateFormat.parseTime(request.time(), "지출 시각");
        LocalDate start = localDate(RowSupport.value(trip, "start_date"));
        LocalDate end = localDate(RowSupport.value(trip, "end_date"));
        if (date.isBefore(start) || date.isAfter(end)) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_DATE_OUTSIDE_TRIP);
        }
        Set<Long> participantIds = new LinkedHashSet<>(request.participantIds());
        if (participantIds.size() != request.participantIds().size()) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PARTICIPANT_DUPLICATED);
        }
        requireJoinedMembers(tripId, participantIds);
        Long payerId = request.paymentSource() == ExpensePaymentSource.PERSONAL
                ? request.payerId() : null;
        if (request.paymentSource() == ExpensePaymentSource.PERSONAL && payerId == null) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PAYER_REQUIRED);
        }
        if (payerId != null) requireJoinedMembers(tripId, Set.of(payerId));
        requireSchedule(tripId, request.scheduleId());
        if (request.paymentSource() == ExpensePaymentSource.SHARED_FUND
                && availableSharedFund(tripId, excludedExpenseId) < request.amount()) {
            throw new BusinessException(ExpenseErrorCode.SHARED_FUND_BALANCE_INSUFFICIENT);
        }
        return new ValidatedExpense(date, time, payerId, List.copyOf(participantIds));
    }

    private void requireJoinedMembers(long tripId, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PARTICIPANT_INVALID);
        }
        String placeholders = String.join(",", Collections.nCopies(userIds.size(), "?"));
        List<Object> params = new ArrayList<>();
        params.add(tripId);
        params.addAll(userIds);
        int count = jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED' AND user_id IN (%s)
                """.formatted(placeholders))
                .params(params.toArray())
                .query(Integer.class)
                .single();
        if (count != userIds.size()) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_PARTICIPANT_INVALID);
        }
    }

    private void requireSchedule(long tripId, Long scheduleId) {
        if (scheduleId == null) return;
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM travel_plan_items i
                JOIN travel_plans p ON p.id = i.plan_id
                WHERE i.id = ? AND p.trip_id = ?
                """)
                .params(scheduleId, tripId)
                .query(Long.class)
                .single();
        if (count == 0) {
            throw new BusinessException(ExpenseErrorCode.EXPENSE_SCHEDULE_INVALID);
        }
    }

    private void replaceParticipants(long expenseId, List<Long> participantIds) {
        jdbc.sql("DELETE FROM trip_expense_participants WHERE expense_id = ?")
                .param(expenseId).update();
        for (Long participantId : participantIds) {
            jdbc.sql("""
                    INSERT INTO trip_expense_participants (expense_id, user_id)
                    VALUES (?, ?)
                    """)
                    .params(expenseId, participantId)
                    .update();
        }
    }

    private SharedFundSummary sharedFundSummary(long tripId) {
        long contributed = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0)
                FROM trip_shared_fund_contributions WHERE trip_id = ?
                """).param(tripId).query(Long.class).single();
        long spent = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0)
                FROM trip_expenses WHERE trip_id = ? AND payment_source = 'SHARED_FUND'
                """).param(tripId).query(Long.class).single();
        return new SharedFundSummary(tripId, contributed, spent, contributed - spent);
    }

    private long availableSharedFund(long tripId, Long excludedExpenseId) {
        SharedFundSummary summary = sharedFundSummary(tripId);
        long available = summary.balance();
        if (excludedExpenseId != null) {
            Map<String, Object> existing = lockedExpense(tripId, excludedExpenseId);
            if ("SHARED_FUND".equals(RowSupport.strValue(existing, "payment_source"))) {
                available = Math.addExact(available, RowSupport.longValue(existing, "amount"));
            }
        }
        return available;
    }

    private ExpenseResponse getResponse(long tripId, long expenseId) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT * FROM trip_expenses WHERE trip_id = ? AND id = ?
                """).params(tripId, expenseId).query().listOfRows();
        if (rows.isEmpty()) throw new BusinessException(ExpenseErrorCode.EXPENSE_NOT_FOUND);
        return responses(rows, participantIds(rows)).getFirst();
    }

    private List<Map<String, Object>> expenseRows(long tripId) {
        return jdbc.sql("""
                SELECT * FROM trip_expenses
                WHERE trip_id = ?
                ORDER BY expense_date, expense_time, id
                """).param(tripId).query().listOfRows();
    }

    private Map<Long, List<Long>> participantIds(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return Map.of();
        List<Long> ids = rows.stream().map(row -> RowSupport.longValue(row, "id")).toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT expense_id, user_id FROM trip_expense_participants
                WHERE expense_id IN (%s) ORDER BY expense_id, user_id
                """.formatted(placeholders))
                .params(ids.toArray())
                .query().listOfRows()
                .forEach(row -> result
                        .computeIfAbsent(RowSupport.longValue(row, "expense_id"), ignored -> new ArrayList<>())
                        .add(RowSupport.longValue(row, "user_id")));
        return result;
    }

    private List<ExpenseResponse> responses(
            List<Map<String, Object>> rows, Map<Long, List<Long>> participants) {
        return rows.stream().map(row -> {
            long id = RowSupport.longValue(row, "id");
            return new ExpenseResponse(
                    id,
                    RowSupport.longValue(row, "trip_id"),
                    nullableLong(row, "schedule_id"),
                    RowSupport.strValue(row, "title"),
                    RowSupport.strValue(row, "memo"),
                    RowSupport.longValue(row, "amount"),
                    nullableLong(row, "payer_user_id"),
                    participants.getOrDefault(id, List.of()),
                    AppDateFormat.date(localDate(RowSupport.value(row, "expense_date"))),
                    AppDateFormat.time(localTime(RowSupport.value(row, "expense_time"))),
                    ExpenseCategory.valueOf(RowSupport.strValue(row, "category")),
                    ExpensePaymentSource.valueOf(RowSupport.strValue(row, "payment_source")),
                    nullableString(row, "receipt_image_uri"),
                    RowSupport.longValue(row, "created_by"),
                    AppDateFormat.databaseDateTime(RowSupport.value(row, "created_at")),
                    AppDateFormat.databaseDateTime(RowSupport.value(row, "updated_at")));
        }).toList();
    }

    private List<Long> joinedMemberIds(long tripId) {
        return jdbc.sql("""
                SELECT user_id FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED' ORDER BY user_id
                """).param(tripId).query(Long.class).list();
    }

    private Map<String, Object> lockTrip(long tripId) {
        return jdbc.sql("SELECT * FROM trips WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(tripId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
    }

    private Map<String, Object> lockedExpense(long tripId, long expenseId) {
        return jdbc.sql("SELECT * FROM trip_expenses WHERE trip_id = ? AND id = ? FOR UPDATE")
                .params(tripId, expenseId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new BusinessException(ExpenseErrorCode.EXPENSE_NOT_FOUND));
    }

    private String normalizedMemo(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Object nullable(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = nullable(row, key);
        return value == null ? null : value instanceof Number number
                ? number.longValue() : Long.parseLong(value.toString());
    }

    private String nullableString(Map<String, Object> row, String key) {
        Object value = nullable(row, key);
        return value == null ? null : value.toString();
    }

    private LocalDate localDate(Object value) {
        return AppDateFormat.databaseDate(value);
    }

    private LocalTime localTime(Object value) {
        return AppDateFormat.databaseTime(value);
    }

    private record ValidatedExpense(
            LocalDate date,
            LocalTime time,
            Long payerId,
            List<Long> participantIds) {
    }
}
