package com.gayadi.server.expense;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.expense.dto.request.ExpenseRequest;
import com.gayadi.server.expense.dto.response.SharedFundSummary;
import com.gayadi.server.expense.model.ExpenseCategory;
import com.gayadi.server.expense.model.ExpensePaymentSource;
import com.gayadi.server.expense.query.ExpenseQueryResult;
import com.gayadi.server.expense.query.TripDateRangeQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 여행 경비와 정산 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class ExpenseRepository {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public ExpenseRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 여행에 등록된 경비 전체를 조회합니다. */
    public List<ExpenseQueryResult> findAll(long tripId) {
        return jdbc.sql("""
                SELECT * FROM trip_expenses
                WHERE trip_id = ? ORDER BY expense_date, expense_time, id
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapExpense)
                .toList();
    }

    /** 식별자에 해당하는 여행 경비를 조회합니다. */
    public Optional<ExpenseQueryResult> find(long tripId, long expenseId) {
        return jdbc.sql("SELECT * FROM trip_expenses WHERE trip_id = ? AND id = ?")
                .params(tripId, expenseId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapExpense);
    }

    /** 여행 경비 정보를 DB에서 조회합니다. */
    public Optional<ExpenseQueryResult> findForUpdate(long tripId, long expenseId) {
        return jdbc.sql("SELECT * FROM trip_expenses WHERE trip_id = ? AND id = ? FOR UPDATE")
                .params(tripId, expenseId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapExpense);
    }

    /** 변경 충돌을 막기 위해 여행 DB 행을 잠급니다. */
    public Optional<TripDateRangeQueryResult> lockTrip(long tripId) {
        return jdbc.sql("SELECT start_date, end_date FROM trips WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(row -> new TripDateRangeQueryResult(
                        AppDateFormat.databaseDate(RowSupport.value(row, "start_date")),
                        AppDateFormat.databaseDate(RowSupport.value(row, "end_date"))));
    }

    /** 결제자·금액·분담자를 포함한 여행 경비를 저장합니다. */
    public long create(
            long tripId,
            long actorId,
            ExpenseRequest request,
            Long payerId,
            java.time.LocalDate date,
            java.time.LocalTime time) {
        return keyHelper.insert("""
                INSERT INTO trip_expenses
                    (trip_id, schedule_id, title, memo, amount, payer_user_id,
                     expense_date, expense_time, category, payment_source,
                     receipt_image_uri, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                tripId,
                request.scheduleId(),
                request.title().trim(),
                normalizedMemo(request.memo()),
                request.amount(),
                payerId,
                date,
                time,
                request.category().name(),
                request.paymentSource().name(),
                blankToNull(request.receiptImageUri()),
                actorId);
    }

    /** 여행 경비의 결제 정보와 분담 기준을 수정합니다. */
    public void update(long tripId, long expenseId, ExpenseRequest request,
            Long payerId, java.time.LocalDate date, java.time.LocalTime time) {
        jdbc.sql("""
                UPDATE trip_expenses
                SET schedule_id = ?, title = ?, memo = ?, amount = ?, payer_user_id = ?,
                    expense_date = ?, expense_time = ?, category = ?, payment_source = ?,
                    receipt_image_uri = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ?
                """).params(request.scheduleId(), request.title().trim(), normalizedMemo(request.memo()),
                request.amount(), payerId, date, time, request.category().name(),
                request.paymentSource().name(), blankToNull(request.receiptImageUri()), expenseId, tripId)
                .update();
    }

    /** 식별자에 해당하는 여행 경비를 삭제합니다. */
    public boolean delete(long tripId, long expenseId) {
        return jdbc.sql("DELETE FROM trip_expenses WHERE id = ? AND trip_id = ?")
                .params(expenseId, tripId)
                .update() > 0;
    }

    /** 참여 중 참여자 여부나 개수를 DB에서 확인합니다. */
    public boolean areJoinedMembers(long tripId, Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return false;
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
        return count == userIds.size();
    }

    /** 일정에 대한 여행 경비 기능을 처리합니다. */
    public boolean scheduleExists(long tripId, long scheduleId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM travel_plan_items i
                JOIN travel_plans p ON p.id = i.plan_id
                WHERE i.id = ? AND p.trip_id = ?
                """)
                .params(scheduleId, tripId)
                .query(Long.class)
                .single() > 0;
    }

    /** 참여자 정보를 새 값으로 교체합니다. */
    public void replaceParticipants(long expenseId, List<Long> participantIds) {
        jdbc.sql("DELETE FROM trip_expense_participants WHERE expense_id = ?")
        .param(expenseId)
        .update();
        participantIds.forEach(participantId -> jdbc.sql("""
                INSERT INTO trip_expense_participants (expense_id, user_id) VALUES (?, ?)
                """)
                .params(expenseId, participantId)
                .update());
    }

    /** 참여자 식별자 목록 조건에 맞는 여행 경비 데이터를 DB에서 조회합니다. */
    public Map<Long, List<Long>> findParticipantIds(List<Long> expenseIds) {
        if (expenseIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(expenseIds.size(), "?"));
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT expense_id, user_id FROM trip_expense_participants
                WHERE expense_id IN (%s) ORDER BY expense_id, user_id
                """.formatted(placeholders))
                .params(expenseIds.toArray())
                .query()
                .listOfRows()
                .forEach(row -> result.computeIfAbsent(
                                RowSupport.longValue(row, "expense_id"),
                                ignored -> new ArrayList<>())
                        .add(RowSupport.longValue(row, "user_id")));
        return result;
    }

    /** 참여 중 참여자 식별자 목록 조건에 맞는 여행 경비 데이터를 DB에서 조회합니다. */
    public List<Long> findJoinedMemberIds(long tripId) {
        return jdbc.sql("""
                SELECT user_id FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED' ORDER BY user_id
                """)
                .param(tripId)
                .query(Long.class)
                .list();
    }

    /** 공동 자금 현황 관련 여행 경비 업무를 처리합니다. */
    public SharedFundSummary sharedFundSummary(long tripId) {
        long contributed = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM trip_shared_fund_contributions WHERE trip_id = ?
                """)
                .param(tripId)
                .query(Long.class)
                .single();
        long spent = jdbc.sql("""
                SELECT COALESCE(SUM(amount), 0) FROM trip_expenses
                WHERE trip_id = ? AND payment_source = 'SHARED_FUND'
                """)
                .param(tripId)
                .query(Long.class)
                .single();
        return new SharedFundSummary(tripId, contributed, spent, contributed - spent);
    }

    /** 공동 자금에 사용자의 분담금을 적립합니다. */
    public void contribute(long tripId, long amount, long actorId) {
        keyHelper.insert("""
                INSERT INTO trip_shared_fund_contributions (trip_id, amount, created_by) VALUES (?, ?, ?)
                """, tripId, amount, actorId);
    }

    private ExpenseQueryResult mapExpense(Map<String, Object> row) {
        return new ExpenseQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "trip_id"),
                nullableLong(row, "schedule_id"),
                RowSupport.strValue(row, "title"),
                RowSupport.strValue(row, "memo"),
                RowSupport.longValue(row, "amount"),
                nullableLong(row, "payer_user_id"),
                AppDateFormat.databaseDate(RowSupport.value(row, "expense_date")),
                AppDateFormat.databaseTime(RowSupport.value(row, "expense_time")),
                ExpenseCategory.valueOf(RowSupport.strValue(row, "category")),
                ExpensePaymentSource.valueOf(RowSupport.strValue(row, "payment_source")),
                nullableString(row, "receipt_image_uri"),
                RowSupport.longValue(row, "created_by"),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "created_at")),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "updated_at")));
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

    private String normalizedMemo(String value) {
        return value == null ? "" : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
