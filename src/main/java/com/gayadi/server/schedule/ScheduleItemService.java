package com.gayadi.server.schedule;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ScheduleItemService {

    private static final DateTimeFormatter APP_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter APP_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcClient jdbc;
    private final TripService trips;
    private final KeyHelper keyHelper;

    public ScheduleItemService(JdbcClient jdbc, TripService trips, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.keyHelper = keyHelper;
    }

    public List<Map<String, Object>> list(long userId, long tripId) {
        trips.requireMember(tripId, userId);
        return jdbc.sql("""
                SELECT i.id, tp.trip_id, i.title, i.place_id, tp.plan_date,
                       i.planned_start, i.schedule_type, i.sequence_no, i.is_visited,
                       i.status, p.name AS place_name,
                       ROW_NUMBER() OVER (
                           ORDER BY tp.plan_date, i.sequence_no, i.id) - 1 AS global_order
                FROM travel_plan_items i
                JOIN travel_plans tp ON tp.id = i.plan_id
                LEFT JOIN places p ON p.id = i.place_id
                WHERE tp.trip_id = ?
                ORDER BY tp.plan_date, i.sequence_no, i.id
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(long userId, long tripId, ScheduleCommand command) {
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);
        validateCommand(command);
        validateDate(trip, command.date());
        requirePlace(command.placeId(), tripId, userId);
        long planId = planForDate(trip, tripId, command.date(), userId);
        int sequence = nextSequence(planId);
        LocalDateTime start = LocalDateTime.of(command.date(), command.time());
        long itemId = keyHelper.insert("""
                INSERT INTO travel_plan_items
                    (plan_id, place_id, item_type, title, sequence_no, planned_start,
                     planned_end, status, schedule_type, is_visited)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PLANNED', ?, FALSE)
                """,
                planId, command.placeId(), command.placeId() == null ? "CUSTOM" : "PLACE",
                command.title().trim(), sequence, start, start.plusHours(1), command.type().name());
        incrementPlanVersion(planId);
        expireTripRoutes(tripId);
        return item(tripId, itemId);
    }

    @Transactional
    public Map<String, Object> update(
            long userId,
            long tripId,
            long scheduleId,
            SchedulePatch patch) {
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);
        Map<String, Object> current = lockedItem(tripId, scheduleId);
        ScheduleCommand command = mergedCommand(current, patch);
        boolean visited = patch.isVisited() == null
                ? Boolean.TRUE.equals(nullable(current, "is_visited"))
                : patch.isVisited();
        validateCommand(command);
        validateDate(trip, command.date());
        requirePlace(command.placeId(), tripId, userId);

        long oldPlanId = RowSupport.longValue(current, "plan_id");
        long newPlanId = planForDate(trip, tripId, command.date(), userId);
        int sequence = RowSupport.intValue(current, "sequence_no");
        if (oldPlanId != newPlanId) {
            jdbc.sql("UPDATE travel_plan_items SET sequence_no = ? WHERE id = ?")
                    .params(-scheduleId, scheduleId)
                    .update();
            sequence = nextSequence(newPlanId);
        }
        LocalDateTime start = LocalDateTime.of(command.date(), command.time());
        jdbc.sql("""
                UPDATE travel_plan_items
                SET plan_id = ?, place_id = ?, item_type = ?, title = ?, sequence_no = ?,
                    planned_start = ?, planned_end = ?, schedule_type = ?, is_visited = ?,
                    status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .params(newPlanId, command.placeId(), command.placeId() == null ? "CUSTOM" : "PLACE",
                        command.title().trim(), sequence, start, start.plusHours(1), command.type().name(),
                        visited, visited ? "COMPLETED" : "PLANNED", scheduleId)
                .update();
        incrementPlanVersion(newPlanId);
        if (oldPlanId != newPlanId) {
            normalize(oldPlanId);
            incrementPlanVersion(oldPlanId);
        }
        expireTripRoutes(tripId);
        return item(tripId, scheduleId);
    }

    /** 기존 내부 호출과 테스트가 전체 일정 값을 넘길 때 사용하는 호환 진입점이다. */
    @Transactional
    public Map<String, Object> update(
            long userId,
            long tripId,
            long scheduleId,
            ScheduleCommand command,
            boolean visited) {
        return update(userId, tripId, scheduleId, new SchedulePatch(
                command.title(), command.date(), command.time(), command.type(),
                command.placeId(), true, visited));
    }

    @Transactional
    public void delete(long userId, long tripId, long scheduleId) {
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);
        Map<String, Object> current = lockedItem(tripId, scheduleId);
        long planId = RowSupport.longValue(current, "plan_id");
        expireTripRoutes(tripId);
        jdbc.sql("""
                UPDATE travel_routes
                SET from_plan_item_id = CASE WHEN from_plan_item_id = ? THEN NULL ELSE from_plan_item_id END,
                    to_plan_item_id = CASE WHEN to_plan_item_id = ? THEN NULL ELSE to_plan_item_id END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE from_plan_item_id = ? OR to_plan_item_id = ?
                """)
                .params(scheduleId, scheduleId, scheduleId, scheduleId)
                .update();
        jdbc.sql("""
                UPDATE travel_supplies
                SET plan_item_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE plan_item_id = ?
                """).param(scheduleId).update();
        jdbc.sql("UPDATE notifications SET plan_item_id = NULL WHERE plan_item_id = ?")
                .param(scheduleId).update();
        jdbc.sql("DELETE FROM travel_plan_items WHERE id = ?").param(scheduleId).update();
        normalize(planId);
        incrementPlanVersion(planId);
    }

    @Transactional
    public List<Map<String, Object>> reorder(long userId, long tripId, List<Long> scheduleIds) {
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "일정 순서를 하나 이상 보내 주세요.");
        }
        Set<Long> unique = new LinkedHashSet<>(scheduleIds);
        if (unique.size() != scheduleIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "같은 일정이 순서 목록에 두 번 들어 있습니다.");
        }
        List<Map<String, Object>> existing = jdbc.sql("""
                SELECT i.id, i.plan_id
                FROM travel_plan_items i JOIN travel_plans p ON p.id = i.plan_id
                WHERE p.trip_id = ? FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows();
        Set<Long> existingIds = existing.stream()
                .map(row -> RowSupport.longValue(row, "id"))
                .collect(java.util.stream.Collectors.toSet());
        if (!existingIds.equals(unique)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "현재 여행의 모든 일정을 빠짐없이 보내 주세요.");
        }
        Map<Long, Long> planByItem = new HashMap<>();
        existing.forEach(row -> planByItem.put(
                RowSupport.longValue(row, "id"), RowSupport.longValue(row, "plan_id")));
        jdbc.sql("""
                UPDATE travel_plan_items SET sequence_no = -sequence_no - 100000
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                """)
                .param(tripId)
                .update();
        Map<Long, Integer> nextByPlan = new HashMap<>();
        for (Long scheduleId : scheduleIds) {
            long planId = planByItem.get(scheduleId);
            int sequence = nextByPlan.merge(planId, 1, Integer::sum);
            jdbc.sql("UPDATE travel_plan_items SET sequence_no = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")
                    .params(sequence, scheduleId)
                    .update();
        }
        jdbc.sql("""
                UPDATE travel_plans
                SET version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ?
                """)
                .param(tripId)
                .update();
        expireTripRoutes(tripId);
        return list(userId, tripId);
    }

    private Map<String, Object> lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT * FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private void incrementPlanVersion(long planId) {
        jdbc.sql("""
                UPDATE travel_plans
                SET version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .param(planId)
                .update();
    }

    private void expireTripRoutes(long tripId) {
        jdbc.sql("""
                UPDATE travel_routes
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .update();
    }

    private Map<String, Object> item(long tripId, long itemId) {
        return jdbc.sql("""
                WITH ranked_items AS (
                    SELECT i.id, tp.trip_id, i.title, i.place_id, tp.plan_date,
                           i.planned_start, i.schedule_type, i.sequence_no, i.is_visited,
                           i.status, p.name AS place_name,
                           ROW_NUMBER() OVER (
                               ORDER BY tp.plan_date, i.sequence_no, i.id) - 1 AS global_order
                    FROM travel_plan_items i
                    JOIN travel_plans tp ON tp.id = i.plan_id
                    LEFT JOIN places p ON p.id = i.place_id
                    WHERE tp.trip_id = ?
                )
                SELECT * FROM ranked_items WHERE id = ?
                """)
                .params(tripId, itemId)
                .query().listOfRows().stream()
                .findFirst()
                .map(this::toView)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
    }

    private Map<String, Object> lockedItem(long tripId, long itemId) {
        return jdbc.sql("""
                SELECT i.id, i.plan_id, i.title, i.place_id, p.plan_date,
                       i.planned_start, i.schedule_type, i.sequence_no, i.is_visited
                FROM travel_plan_items i JOIN travel_plans p ON p.id = i.plan_id
                WHERE p.trip_id = ? AND i.id = ? FOR UPDATE
                """)
                .params(tripId, itemId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
    }

    private long planForDate(
            Map<String, Object> trip,
            long tripId,
            LocalDate date,
            long userId) {
        Long planId = jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? AND plan_date = ?")
                .params(tripId, date)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (planId != null) return planId;
        LocalDate startDate = localDate(RowSupport.value(trip, "start_date"));
        int day = Math.toIntExact(ChronoUnit.DAYS.between(startDate, date)) + 1;
        var inserted = keyHelper.insertOrEmptyOnUniqueViolation("""
                INSERT INTO travel_plans
                    (trip_id, plan_date, day_number, title, source_type, status, created_by, version)
                VALUES (?, ?, ?, ?, 'MANUAL', 'DRAFT', ?, 0)
                """, tripId, date, day, day + "일차 일정", userId);
        if (inserted.isPresent()) {
            return inserted.getAsLong();
        }
        return jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? AND plan_date = ?")
                .params(tripId, date)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "일정표를 만들지 못했습니다."));
    }

    private int nextSequence(long planId) {
        return jdbc.sql("SELECT COALESCE(MAX(sequence_no), 0) + 1 FROM travel_plan_items WHERE plan_id = ?")
                .param(planId)
                .query(Integer.class)
                .single();
    }

    private void normalize(long planId) {
        List<Long> ids = jdbc.sql("""
                SELECT id FROM travel_plan_items WHERE plan_id = ? ORDER BY sequence_no, id
                """)
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

    private void validateDate(Map<String, Object> trip, LocalDate date) {
        LocalDate start = localDate(RowSupport.value(trip, "start_date"));
        LocalDate end = localDate(RowSupport.value(trip, "end_date"));
        if (date.isBefore(start) || date.isAfter(end)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "일정 날짜는 여행 기간 안이어야 합니다.");
        }
    }

    private ScheduleCommand mergedCommand(Map<String, Object> current, SchedulePatch patch) {
        String title = patch.title() == null
                ? RowSupport.strValue(current, "title")
                : patch.title();
        LocalDate date = patch.date() == null
                ? localDate(RowSupport.value(current, "plan_date"))
                : patch.date();
        Object rawStart = nullable(current, "planned_start");
        LocalTime currentTime = rawStart == null
                ? LocalTime.MIDNIGHT
                : localDateTime(rawStart).toLocalTime();
        LocalTime time = patch.time() == null ? currentTime : patch.time();
        ScheduleType type = patch.type() == null
                ? ScheduleType.valueOf(RowSupport.strValue(current, "schedule_type"))
                : patch.type();
        Long placeId = patch.placeIdPresent()
                ? patch.placeId()
                : nullableLong(current, "place_id");
        return new ScheduleCommand(title, date, time, type, placeId);
    }

    private void validateCommand(ScheduleCommand command) {
        if (command == null || command.title() == null || command.title().isBlank()
                || command.title().trim().length() > 200) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "일정 이름은 1자에서 200자 사이여야 합니다.");
        }
        if (command.date() == null || command.time() == null || command.type() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "일정 날짜, 시각과 종류가 필요합니다.");
        }
    }

    private void requireEditableTrip(Map<String, Object> trip) {
        String status = RowSupport.strValue(trip, "status");
        if ("COMPLETED".equals(status) || "CANCELED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "완료되거나 취소된 여행의 일정은 바꿀 수 없습니다.");
        }
    }

    private void requirePlace(Long placeId, long tripId, long userId) {
        if (placeId == null) return;
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM places
                WHERE id = ? AND status = 'ACTIVE'
                  AND (visibility = 'PUBLIC' OR owner_user_id = ? OR trip_id = ?)
                """)
                .params(placeId, userId, tripId)
                .query(Long.class)
                .single();
        if (count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다.");
        }
    }

    private Map<String, Object> toView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", RowSupport.longValue(row, "id"));
        result.put("tripId", RowSupport.longValue(row, "trip_id"));
        result.put("title", RowSupport.strValue(row, "title"));
        result.put("placeId", nullable(row, "place_id"));
        result.put("placeName", nullable(row, "place_name"));
        LocalDate date = localDate(RowSupport.value(row, "plan_date"));
        result.put("date", date.format(APP_DATE));
        Object rawStart = nullable(row, "planned_start");
        LocalDateTime start = rawStart == null
                ? date.atStartOfDay()
                : rawStart instanceof LocalDateTime value
                ? value
                : rawStart instanceof java.sql.Timestamp value
                ? value.toLocalDateTime()
                : LocalDateTime.parse(rawStart.toString().replace(' ', 'T'));
        result.put("time", start.toLocalTime().format(APP_TIME));
        result.put("type", RowSupport.strValue(row, "schedule_type"));
        Object rawGlobalOrder = nullable(row, "global_order");
        result.put("order", rawGlobalOrder == null
                ? RowSupport.intValue(row, "sequence_no") - 1
                : ((Number) rawGlobalOrder).intValue());
        result.put("isVisited", Boolean.TRUE.equals(nullable(row, "is_visited")));
        return result;
    }

    private Object nullable(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase());
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = nullable(row, key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        return LocalDate.parse(value.toString().substring(0, 10));
    }

    public enum ScheduleType {
        MAIN,
        ALTERNATIVE
    }

    public record ScheduleCommand(
            String title,
            LocalDate date,
            LocalTime time,
            ScheduleType type,
            Long placeId
    ) {
    }

    public record SchedulePatch(
            String title,
            LocalDate date,
            LocalTime time,
            ScheduleType type,
            Long placeId,
            boolean placeIdPresent,
            Boolean isVisited
    ) {
    }
}
