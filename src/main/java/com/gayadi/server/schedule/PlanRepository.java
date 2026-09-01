package com.gayadi.server.schedule;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.schedule.query.*;
import com.gayadi.server.travel.model.TripStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 여행 일정과 계획 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class PlanRepository {
    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public PlanRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 계획 생성 충돌을 막고 여행 기간·상태를 함께 조회합니다. */
    public Optional<PlanTripQueryResult> lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT id, owner_id, region_id, start_date, end_date, status
                FROM trips WHERE id = ? AND deleted_at IS NULL FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapTrip);
    }

    /** 일차 계획 정보를 DB에서 조회합니다. */
    public List<PlanDayQueryResult> findDays(long tripId) {
        return jdbc.sql("""
                SELECT id, trip_id, day_number, plan_date, title, description, source_type,
                       status, preference_snapshot, created_by, version, created_at, updated_at
                FROM travel_plans WHERE trip_id = ? ORDER BY day_number
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapDay)
                .toList();
    }

    /** 계획 식별자 정보를 DB에서 조회합니다. */
    public Map<Integer, Long> findPlanIdsByDay(long tripId) {
        Map<Integer, Long> result = new LinkedHashMap<>();
        jdbc.sql("SELECT id, day_number FROM travel_plans WHERE trip_id = ? ORDER BY day_number")
                .param(tripId)
                .query()
                .listOfRows()
                .forEach(row -> result.putIfAbsent(
                        intValue(row, "day_number"),
                        longValue(row, "id")));
        return result;
    }

    /** 항목 목록 조건에 맞는 여행 계획 데이터를 DB에서 조회합니다. */
    public List<PlanItemQueryResult> findItems(List<Long> planIds) {
        if (planIds.isEmpty()) {
            return List.of();
        }
        String placeholders = placeholders(planIds.size());
        return jdbc.sql("""
                SELECT i.plan_id, i.id, i.sequence_no, i.planned_start, i.planned_end,
                       i.status, i.item_type, i.title, i.description, i.estimated_cost, i.memo,
                       p.id AS place_id, p.name AS place_name, p.category, p.address,
                       p.latitude, p.longitude
                FROM travel_plan_items i LEFT JOIN places p ON p.id = i.place_id
                WHERE i.plan_id IN (%s) ORDER BY i.plan_id, i.sequence_no
                """.formatted(placeholders))
                .params(planIds)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapItem)
                .toList();
    }

    /** 후보 목록 조건에 맞는 여행 계획 데이터를 DB에서 조회합니다. */
    public List<PlanPlaceQueryResult> findCandidates(
            long tripId,
            long regionId,
            char placeProfile,
            char energyProfile,
            char preparationProfile,
            int limit) {
        return jdbc.sql("""
                SELECT id, name, category, address, latitude, longitude
                FROM places WHERE region_id = ? AND status = 'ACTIVE' AND category <> 'SHELTER'
                  AND (visibility = 'PUBLIC' OR trip_id = ?)
                ORDER BY
                  CASE ? WHEN 'N' THEN CASE WHEN category = 'ATTRACTION' AND COALESCE(indoor, FALSE) = FALSE THEN 0 ELSE 1 END
                         WHEN 'C' THEN CASE WHEN category IN ('CULTURE','SHOPPING','CAFE','RESTAURANT') OR COALESCE(indoor,FALSE)=TRUE THEN 0 ELSE 1 END ELSE 1 END,
                  CASE ? WHEN 'A' THEN CASE WHEN category IN ('ATTRACTION','SHOPPING') OR COALESCE(basic_info,'') LIKE '%"pace":"ACTIVE"%' THEN 0 ELSE 1 END
                         WHEN 'R' THEN CASE WHEN category IN ('CULTURE','CAFE','RESTAURANT','ACCOMMODATION') OR COALESCE(basic_info,'') LIKE '%"pace":"RELAXED"%' THEN 0 ELSE 1 END ELSE 1 END,
                  CASE WHEN ? = 'S' THEN id ELSE 0 END DESC, id LIMIT ?
                """)
                .params(
                        regionId,
                        tripId,
                        String.valueOf(placeProfile),
                        String.valueOf(energyProfile),
                        String.valueOf(preparationProfile),
                        limit)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapPlace)
                .toList();
    }

    /** 계획 여행 계획 데이터를 DB에 저장합니다. */
    public long insertPlan(
            long tripId,
            LocalDate date,
            int day,
            String title,
            long ownerId,
            String preferenceSnapshot) {
        return keyHelper.insert("""
                INSERT INTO travel_plans (trip_id, plan_date, day_number, title, source_type,
                                          status, created_by, version, preference_snapshot)
                VALUES (?, ?, ?, ?, 'AI', 'DRAFT', ?, 0, ?)
                """,
                tripId,
                date,
                day,
                title,
                ownerId,
                preferenceSnapshot);
    }

    /** 계획 여행 계획 상태를 DB에 반영합니다. */
    public void updatePlan(
            long planId,
            long tripId,
            LocalDate date,
            String title,
            String preferenceSnapshot) {
        jdbc.sql("""
                UPDATE travel_plans SET plan_date = ?, title = ?, source_type = 'AI', status = 'DRAFT',
                    version = version + 1, preference_snapshot = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ?
                """)
                .params(
                        date,
                        title,
                        preferenceSnapshot,
                        planId,
                        tripId)
                .update();
    }

    /** 항목 목록 여행 계획 데이터를 DB에서 삭제합니다. */
    public void deleteItems(List<Long> planIds) {
        if (planIds.isEmpty()) {
            return;
        }

        String placeholders = placeholders(planIds.size());

        jdbc.sql("""
                UPDATE travel_routes
                SET from_plan_item_id = NULL,
                    to_plan_item_id = NULL,
                    status = 'EXPIRED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (%s)
                """.formatted(placeholders))
                .params(planIds)
                .update();
        jdbc.sql("""
                UPDATE travel_supplies
                SET plan_item_id = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE plan_item_id IN (
                    SELECT id
                    FROM travel_plan_items
                    WHERE plan_id IN (%s)
                )
                """.formatted(placeholders))
                .params(planIds)
                .update();
        jdbc.sql("""
                UPDATE notifications
                SET plan_item_id = NULL
                WHERE plan_item_id IN (
                    SELECT id
                    FROM travel_plan_items
                    WHERE plan_id IN (%s)
                )
                """.formatted(placeholders))
                .params(planIds)
                .update();
        jdbc.sql("DELETE FROM travel_plan_items WHERE plan_id IN (" + placeholders + ")")
                .params(planIds)
                .update();
    }

    /** 계획 정보를 DB에서 삭제합니다. */
    public void deletePlansAfterDay(long tripId, int lastDay) {
        List<Long> ids = jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? AND day_number > ?")
                .params(tripId, lastDay)
                .query(Long.class)
                .list();
        if (ids.isEmpty()) {
            return;
        }

        String placeholders = placeholders(ids.size());

        jdbc.sql("""
                UPDATE notifications
                SET route_id = NULL
                WHERE route_id IN (
                    SELECT id
                    FROM travel_routes
                    WHERE plan_id IN (%s)
                )
                """.formatted(placeholders))
                .params(ids)
                .update();
        jdbc.sql("""
                UPDATE notifications
                SET proposal_id = NULL
                WHERE proposal_id IN (
                    SELECT id
                    FROM ai_schedule_change_proposals
                    WHERE plan_id IN (%s)
                )
                """.formatted(placeholders))
                .params(ids)
                .update();
        jdbc.sql("UPDATE notifications SET plan_id = NULL WHERE plan_id IN (" + placeholders + ")")
                .params(ids)
                .update();
        jdbc.sql("DELETE FROM travel_plans WHERE id IN (" + placeholders + ")")
                .params(ids)
                .update();
    }

    /** 항목 목록 여행 계획 데이터를 DB에 저장합니다. */
    public void insertItems(List<GeneratedPlanItem> items) {
        if (items.isEmpty()) {
            return;
        }
        String values = String.join(", ", Collections.nCopies(items.size(), "(?, ?, ?, ?, ?, ?, ?, 'PLANNED')"));
        List<Object> params = new ArrayList<>(items.size() * 7);
        items.forEach(item -> Collections.addAll(params, item.planId(), item.placeId(), item.itemType(),
                item.title(), item.sequenceNo(), item.startsAt(), item.endsAt()));
        jdbc.sql("""
                INSERT INTO travel_plan_items (plan_id, place_id, item_type, title, sequence_no,
                                               planned_start, planned_end, status) VALUES %s
                """.formatted(values))
                .params(params)
                .update();
    }

    /** 첫·마지막 장소 정보를 DB에서 조회합니다. */
    public Optional<PlanPlaceQueryResult> findBoundaryPlace(long tripId, boolean descending) {
        String order = descending ? "tp.day_number DESC, i.sequence_no DESC" : "tp.day_number ASC, i.sequence_no ASC";
        return jdbc.sql("""
                SELECT p.id, p.name, p.category, p.address, p.latitude, p.longitude
                FROM travel_plans tp JOIN travel_plan_items i ON i.plan_id = tp.id
                JOIN places p ON p.id = i.place_id WHERE tp.trip_id = ?
                ORDER BY %s LIMIT 1
                """.formatted(order))
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapPlace);
    }

    private PlanTripQueryResult mapTrip(Map<String, Object> row) {
        return new PlanTripQueryResult(
                longValue(row, "id"),
                longValue(row, "owner_id"),
                longValue(row, "region_id"),
                date(row, "start_date"),
                date(row, "end_date"),
                TripStatus.valueOf(text(row, "status")));
    }

    private PlanDayQueryResult mapDay(Map<String, Object> row) {
        return new PlanDayQueryResult(
                longValue(row, "id"),
                longValue(row, "trip_id"),
                intValue(row, "day_number"),
                date(row, "plan_date"),
                text(row, "title"),
                optionalText(row, "description"),
                text(row, "source_type"),
                text(row, "status"),
                optionalText(row, "preference_snapshot"),
                longValue(row, "created_by"),
                intValue(row, "version"),
                dateTime(row, "created_at"),
                dateTime(row, "updated_at"));
    }

    private PlanItemQueryResult mapItem(Map<String, Object> row) {
        return new PlanItemQueryResult(
                longValue(row, "plan_id"),
                longValue(row, "id"),
                intValue(row, "sequence_no"),
                dateTime(row, "planned_start"),
                dateTime(row, "planned_end"),
                optionalText(row, "status"),
                optionalText(row, "item_type"),
                optionalText(row, "title"),
                optionalText(row, "description"),
                optionalInt(row, "estimated_cost"),
                optionalText(row, "memo"),
                optionalLong(row, "place_id"),
                optionalText(row, "place_name"),
                optionalText(row, "category"),
                optionalText(row, "address"),
                optionalDouble(row, "latitude"),
                optionalDouble(row, "longitude"));
    }

    private PlanPlaceQueryResult mapPlace(Map<String, Object> row) {
        return new PlanPlaceQueryResult(
                longValue(row, "id"),
                text(row, "name"),
                text(row, "category"),
                optionalText(row, "address"),
                optionalDouble(row, "latitude"),
                optionalDouble(row, "longitude"));
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private long longValue(Map<String, Object> row, String key) {
        return ((Number) raw(row, key)).longValue();
    }

    private int intValue(Map<String, Object> row, String key) {
        return ((Number) raw(row, key)).intValue();
    }

    private String text(Map<String, Object> row, String key) {
        return raw(row, key).toString();
    }

    private String optionalText(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value.toString();
    }

    private Long optionalLong(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer optionalInt(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : ((Number) value).intValue();
    }

    private Double optionalDouble(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : ((Number) value).doubleValue();
    }

    private LocalDate date(Map<String, Object> row, String key) {
        return AppDateFormat.databaseDate(raw(row, key));
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }

    private String placeholders(int size) {
        return String.join(", ", Collections.nCopies(size, "?"));
    }

    public record GeneratedPlanItem(
            long planId,
            long placeId,
            String itemType,
            String title,
            int sequenceNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
    }
}
