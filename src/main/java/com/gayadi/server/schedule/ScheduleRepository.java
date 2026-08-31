package com.gayadi.server.schedule;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.schedule.query.EditableScheduleItemQueryResult;
import com.gayadi.server.schedule.query.ScheduleTripQueryResult;
import com.gayadi.server.travel.model.TripStatus;
import com.gayadi.server.schedule.model.ScheduleType;
import com.gayadi.server.schedule.query.ScheduleItemQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

/** 여행 일정과 계획 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class ScheduleRepository {
    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public ScheduleRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 여행의 취소되지 않은 일정 항목을 날짜와 순서대로 조회합니다. */
    public List<ScheduleItemQueryResult> findAllItems(long tripId) {
        return jdbc.sql("""
                SELECT i.id, tp.trip_id, i.plan_id, i.title, i.place_id, tp.plan_date,
                       i.planned_start, i.planned_end, i.memo, i.schedule_type,
                       i.sequence_no, i.is_visited, p.name AS place_name,
                       ROW_NUMBER() OVER (ORDER BY tp.plan_date, i.sequence_no, i.id) - 1 AS global_order
                FROM travel_plan_items i
                JOIN travel_plans tp ON tp.id = i.plan_id
                LEFT JOIN places p ON p.id = i.place_id
                WHERE tp.trip_id = ?
                ORDER BY tp.plan_date, i.sequence_no, i.id
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapItem)
                .toList();
    }

    /** 항목 조건에 맞는 여행 일정 데이터를 DB에서 조회합니다. */
    public Optional<ScheduleItemQueryResult> findItem(long tripId, long itemId) {
        return jdbc.sql("""
                WITH ranked_items AS (
                    SELECT i.id, tp.trip_id, i.plan_id, i.title, i.place_id, tp.plan_date,
                           i.planned_start, i.planned_end, i.memo, i.schedule_type,
                           i.sequence_no, i.is_visited, p.name AS place_name,
                           ROW_NUMBER() OVER (ORDER BY tp.plan_date, i.sequence_no, i.id) - 1 AS global_order
                    FROM travel_plan_items i
                    JOIN travel_plans tp ON tp.id = i.plan_id
                    LEFT JOIN places p ON p.id = i.place_id
                    WHERE tp.trip_id = ?
                ) SELECT * FROM ranked_items WHERE id = ?
                """)
                .params(
                        tripId,
                        itemId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapItem);
    }

    /** 변경 충돌을 막기 위해 여행 DB 행을 잠급니다. */
    public Optional<ScheduleTripQueryResult> lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT id, start_date, end_date, status FROM trips
                WHERE id = ? AND deleted_at IS NULL FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapTrip);
    }

    /** 변경 충돌을 막기 위해 항목 DB 행을 잠급니다. */
    public Optional<EditableScheduleItemQueryResult> lockItem(long tripId, long itemId) {
        return jdbc.sql("""
                SELECT i.id, i.plan_id, i.title, i.place_id, p.plan_date,
                       i.planned_start, i.planned_end, i.memo, i.schedule_type,
                       i.sequence_no, i.is_visited
                FROM travel_plan_items i JOIN travel_plans p ON p.id = i.plan_id
                WHERE p.trip_id = ? AND i.id = ? FOR UPDATE
                """)
                .params(
                        tripId,
                        itemId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapEditableItem);
    }

    /** 항목 여행 일정 데이터를 DB에 저장합니다. */
    public long insertItem(
            long planId,
            Long placeId,
            String title,
            int sequence,
            LocalDateTime start,
            LocalDateTime end,
            String memo,
            ScheduleType type) {
        return keyHelper.insert("""
                INSERT INTO travel_plan_items
                    (plan_id, place_id, item_type, title, sequence_no, planned_start,
                     planned_end, memo, status, schedule_type, is_visited)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, FALSE)
                """,
                planId,
                placeId,
                placeId == null ? "CUSTOM" : "PLACE",
                title,
                sequence,
                start,
                end,
                memo,
                type.name());
    }

    /** 항목 여행 일정 상태를 DB에 반영합니다. */
    public void updateItem(
            long id,
            long planId,
            Long placeId,
            String title,
            int sequence,
            LocalDateTime start,
            LocalDateTime end,
            String memo,
            ScheduleType type,
            boolean visited) {
        jdbc.sql("""
                UPDATE travel_plan_items SET plan_id = ?, place_id = ?, item_type = ?, title = ?,
                    sequence_no = ?, planned_start = ?, planned_end = ?, memo = ?,
                    schedule_type = ?, is_visited = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .params(
                        planId,
                        placeId,
                        placeId == null ? "CUSTOM" : "PLACE",
                        title,
                        sequence,
                        start,
                        end,
                        memo,
                        type.name(),
                        visited,
                        visited ? "COMPLETED" : "PLANNED",
                        id)
                .update();
    }

    /** 순번 관련 여행 일정 업무를 처리합니다. */
    public void reserveSequence(long itemId) {
        jdbc.sql("UPDATE travel_plan_items SET sequence_no = ? WHERE id = ?")
                .params(
                        -itemId,
                        itemId)
                .update();
    }

    /** 계획 버전 상태나 값을 DB에 반영합니다. */
    public void incrementPlanVersion(long planId) {
        jdbc.sql("UPDATE travel_plans SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                .param(planId)
                .update();
    }

    /** 여행 경로 목록 여행 일정 상태를 DB에서 만료 또는 해제합니다. */
    public void expireTripRoutes(long tripId) {
        jdbc.sql("""
                UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .update();
    }

    /** 계획 식별자 조건에 맞는 여행 일정 데이터를 DB에서 조회합니다. */
    public Optional<Long> findPlanId(long tripId, LocalDate date) {
        return jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? AND plan_date = ?")
                .params(tripId, date)
                .query(Long.class)
                .optional();
    }

    /** 계획 정보를 DB에 저장합니다. */
    public Optional<Long> insertManualPlan(long tripId, LocalDate date, int day, long userId) {
        var inserted = keyHelper.insertOrEmptyOnUniqueViolation("""
                INSERT INTO travel_plans
                    (trip_id, plan_date, day_number, title, source_type, status, created_by, version)
                VALUES (?, ?, ?, ?, 'MANUAL', 'DRAFT', ?, 0)
                """, tripId, date, day, day + "일차 일정", userId);
        return inserted.isPresent() ? Optional.of(inserted.getAsLong()) : Optional.empty();
    }

    /** 순번 관련 여행 일정 업무를 처리합니다. */
    public int nextSequence(long planId) {
        return jdbc.sql("SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM travel_plan_items WHERE plan_id = ?")
                .param(planId)
                .query(Integer.class)
                .single();
    }

    /** 일정 항목의 순번을 연속된 값으로 정규화합니다. */
    public void normalize(long planId) {
        List<Long> ids = jdbc.sql("SELECT id FROM travel_plan_items WHERE plan_id = ? ORDER BY sequence_no, id")
                .param(planId)
                .query(Long.class)
                .list();
        jdbc.sql("UPDATE travel_plan_items SET sequence_no = -sequence_no - 100000 WHERE plan_id = ?")
                .param(planId)
                .update();
        for (int index = 0; index < ids.size(); index++) {
            jdbc.sql("UPDATE travel_plan_items SET sequence_no = ? WHERE id = ?")
                    .params(index + 1, ids.get(index))
                    .update();
        }
    }

    /** 장소에 대한 여행 일정 기능을 처리합니다. */
    public boolean placeExists(long placeId, long tripId, long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM places WHERE id = ? AND status = 'ACTIVE'
                  AND (visibility = 'PUBLIC' OR owner_user_id = ? OR trip_id = ?)
                """)
                .params(placeId, userId, tripId)
                .query(Long.class)
                .single() > 0;
    }

    /** 변경 충돌을 막기 위해 항목 계획 목록 DB 행을 잠급니다. */
    public Map<Long, Long> lockItemPlans(long tripId) {
        Map<Long, Long> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT i.id, i.plan_id FROM travel_plan_items i
                JOIN travel_plans p ON p.id = i.plan_id WHERE p.trip_id = ? FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .forEach(row -> result.put(
                        number(row, "id").longValue(),
                        number(row, "plan_id").longValue()));
        return result;
    }

    /** 요청 순서에 맞춰 일정 항목을 재정렬합니다. */
    public void reorder(long tripId, List<Long> itemIds, Map<Long, Long> planByItem) {
        jdbc.sql("""
                UPDATE travel_plan_items SET sequence_no = -sequence_no - 100000
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                """)
                .param(tripId)
                .update();
        Map<Long, Integer> nextByPlan = new LinkedHashMap<>();
        for (Long itemId : itemIds) {
            long planId = planByItem.get(itemId);
            int sequence = nextByPlan.merge(planId, 1, Integer::sum);
            jdbc.sql("UPDATE travel_plan_items SET sequence_no = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                    .params(
                            sequence,
                            itemId)
                    .update();
        }
        jdbc.sql("UPDATE travel_plans SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE trip_id = ?")
                .param(tripId)
                .update();
    }

    /** 항목 여행 일정 데이터를 DB에서 삭제합니다. */
    public void deleteItem(long itemId) {
        jdbc.sql("""
                UPDATE travel_routes
                SET from_plan_item_id = CASE WHEN from_plan_item_id = ? THEN NULL ELSE from_plan_item_id END,
                    to_plan_item_id = CASE WHEN to_plan_item_id = ? THEN NULL ELSE to_plan_item_id END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE from_plan_item_id = ? OR to_plan_item_id = ?
                """)
                .params(
                        itemId,
                        itemId,
                        itemId,
                        itemId)
                .update();
        jdbc.sql("""
                UPDATE travel_supplies
                SET plan_item_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE plan_item_id = ?
                """)
                .param(itemId)
                .update();
        jdbc.sql("UPDATE notifications SET plan_item_id = NULL WHERE plan_item_id = ?")
                .param(itemId)
                .update();
        jdbc.sql("DELETE FROM travel_plan_items WHERE id = ?")
                .param(itemId)
                .update();
    }

    private ScheduleItemQueryResult mapItem(Map<String, Object> row) {
        return new ScheduleItemQueryResult(
                number(row, "id").longValue(),
                number(row, "trip_id").longValue(),
                longOrNull(row, "plan_id"),
                text(row, "title"),
                longOrNull(row, "place_id"),
                textOrNull(row, "place_name"),
                AppDateFormat.databaseDate(raw(row, "plan_date")),
                dateTimeOrNull(row, "planned_start"),
                dateTimeOrNull(row, "planned_end"),
                textOrNull(row, "memo"),
                ScheduleType.valueOf(text(row, "schedule_type")),
                number(row, "sequence_no").intValue(),
                integerOrNull(row, "global_order"),
                Boolean.TRUE.equals(booleanOrNull(row, "is_visited")));
    }

    private ScheduleTripQueryResult mapTrip(Map<String, Object> row) {
        return new ScheduleTripQueryResult(
                number(row, "id").longValue(),
                AppDateFormat.databaseDate(raw(row, "start_date")),
                AppDateFormat.databaseDate(raw(row, "end_date")),
                TripStatus.valueOf(text(row, "status")));
    }

    private EditableScheduleItemQueryResult mapEditableItem(Map<String, Object> row) {
        return new EditableScheduleItemQueryResult(
                number(row, "id").longValue(),
                number(row, "plan_id").longValue(),
                text(row, "title"),
                longOrNull(row, "place_id"),
                AppDateFormat.databaseDate(raw(row, "plan_date")),
                dateTimeOrNull(row, "planned_start"),
                dateTimeOrNull(row, "planned_end"),
                textOrNull(row, "memo"),
                ScheduleType.valueOf(text(row, "schedule_type")),
                number(row, "sequence_no").intValue(),
                Boolean.TRUE.equals(booleanOrNull(row, "is_visited")));
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value instanceof Number number ? number : Long.parseLong(value.toString());
    }

    private String text(Map<String, Object> row, String key) {
        return raw(row, key).toString();
    }

    private String textOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value.toString();
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer integerOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : ((Number) value).intValue();
    }

    private Boolean booleanOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null
                ? null
                : value instanceof Boolean bool
                        ? bool
                        : Boolean.valueOf(value.toString());
    }

    private java.time.LocalDateTime dateTimeOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }
}
