package com.gayadi.server.schedule;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.TripService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PlanService {

    private static final int MAX_PLACES_PER_DAY = 3;
    private static final int MAX_GENERATED_DAYS = 366;

    private final JdbcClient jdbc;
    private final TripService trips;
    private final SurveyService surveys;
    private final JsonSupport json;
    private final KeyHelper keyHelper;

    public PlanService(JdbcClient jdbc, TripService trips, SurveyService surveys, JsonSupport json, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.surveys = surveys;
        this.json = json;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public Map<String, Object> generate(long tripId) {
        Map<String, Object> trip = lockedTrip(tripId);
        if (!"PLANNING".equals(RowSupport.strValue(trip, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 시작 전 일정만 다시 생성할 수 있습니다.");
        }

        Map<String, Object> profile = surveys.groupProfile(tripId);
        String dominant = RowSupport.strValue(profile, "dominantProfile");
        ProfileCode profileCode = ProfileCode.from(dominant);
        long ownerId = RowSupport.longValue(trip, "owner_id");
        long regionId = RowSupport.longValue(trip, "region_id");
        LocalDate startDate = tripDate(trip, "start_date");
        LocalDate endDate = tripDate(trip, "end_date");
        long dayCount = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (dayCount <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 기간이 올바르지 않습니다.");
        }
        if (dayCount > MAX_GENERATED_DAYS) {
            throw new ApiException(HttpStatus.CONFLICT, "자동 일정은 최대 366일까지 생성할 수 있습니다.");
        }

        int candidateLimit = Math.min(1_100,
                Math.max(MAX_PLACES_PER_DAY, Math.toIntExact(dayCount) * MAX_PLACES_PER_DAY));
        List<PlaceCandidate> places = orderedPlaces(
                tripId, regionId, profileCode, candidateLimit);
        if (places.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "해당 지역에 일정으로 추천할 수 있는 장소가 없습니다.");
        }

        List<Map<String, Object>> existingPlans = jdbc.sql("""
                SELECT id, day_number
                FROM travel_plans
                WHERE trip_id = ?
                ORDER BY day_number
                """)
                .param(tripId)
                .query().listOfRows();
        Map<Integer, Long> planIdByDay = existingPlans.stream()
                .collect(Collectors.toMap(
                        row -> RowSupport.intValue(row, "day_number"),
                        row -> RowSupport.longValue(row, "id"),
                        (first, ignored) -> first,
                        LinkedHashMap::new
                ));

        List<Long> existingPlanIds = existingPlans.stream()
                .map(row -> RowSupport.longValue(row, "id"))
                .toList();
        deleteItems(existingPlanIds);

        String preferenceSnapshot = json.write(profile);
        List<PlannedItem> items = new ArrayList<>();
        int numberOfDays = Math.toIntExact(dayCount);
        try {
            for (int dayIndex = 0; dayIndex < numberOfDays; dayIndex++) {
                int dayNumber = dayIndex + 1;
                LocalDate planDate = startDate.plusDays(dayIndex);
                Long planId = planIdByDay.get(dayNumber);
                if (planId == null) {
                    planId = keyHelper.insert("""
                            INSERT INTO travel_plans (trip_id, plan_date, day_number, title, source_type,
                                                      status, created_by, version, preference_snapshot)
                            VALUES (?, ?, ?, ?, 'AI', 'DRAFT', ?, 0, ?)
                            """,
                            tripId, planDate, dayNumber, dayNumber + "일차 일정", ownerId, preferenceSnapshot);
                    planIdByDay.put(dayNumber, planId);
                } else {
                    jdbc.sql("""
                            UPDATE travel_plans
                            SET plan_date = ?, title = ?, source_type = 'AI', status = 'DRAFT',
                                version = version + 1, preference_snapshot = ?, updated_at = CURRENT_TIMESTAMP
                            WHERE id = ? AND trip_id = ?
                            """)
                            .params(planDate, dayNumber + "일차 일정", preferenceSnapshot, planId, tripId)
                            .update();
                }
                items.addAll(itemsForDay(planId, planDate, dayIndex, places, profileCode));
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "일정이 동시에 생성되었습니다. 다시 조회해 주세요.");
        }

        deletePlansAfterDay(tripId, numberOfDays);
        insertItems(items);
        return get(tripId);
    }

    public Map<String, Object> get(long tripId) {
        trips.requireTrip(tripId);
        List<Map<String, Object>> planRows = jdbc.sql("""
                SELECT *
                FROM travel_plans
                WHERE trip_id = ?
                ORDER BY day_number
                """)
                .param(tripId)
                .query().listOfRows();
        if (planRows.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "생성된 일정이 없습니다.");
        }

        List<Long> planIds = planRows.stream()
                .map(row -> RowSupport.longValue(row, "id"))
                .toList();
        Map<Long, List<Map<String, Object>>> itemsByPlan = loadItems(planIds);
        List<Map<String, Object>> days = new ArrayList<>();
        for (Map<String, Object> row : planRows) {
            Map<String, Object> day = new LinkedHashMap<>(row);
            day.put("items", itemsByPlan.getOrDefault(RowSupport.longValue(row, "id"), List.of()));
            days.add(day);
        }

        // 기존 단일 일정 응답의 최상위 필드를 유지하면서 전체 일차도 함께 제공한다.
        Map<String, Object> result = new LinkedHashMap<>(days.getFirst());
        result.put("days", days);
        return result;
    }

    public Map<String, Object> firstPlace(long tripId) {
        return boundaryPlace(tripId, false);
    }

    public Map<String, Object> lastPlace(long tripId) {
        return boundaryPlace(tripId, true);
    }

    private Map<String, Object> boundaryPlace(long tripId, boolean descending) {
        String order = descending
                ? "tp.day_number DESC, i.sequence_no DESC"
                : "tp.day_number ASC, i.sequence_no ASC";
        String sql = """
                SELECT p.*
                FROM travel_plans tp
                JOIN travel_plan_items i ON i.plan_id = tp.id
                LEFT JOIN places p ON p.id = i.place_id
                WHERE tp.trip_id = ? AND p.id IS NOT NULL
                ORDER BY %s
                LIMIT 1
                """.formatted(order);
        return jdbc.sql(sql)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "경로 계산 전에 장소 일정이 필요합니다."));
    }

    private Map<String, Object> lockedTrip(long tripId) {
        return jdbc.sql("""
                SELECT *
                FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private List<PlaceCandidate> orderedPlaces(
            long tripId, long regionId, ProfileCode profile, int limit) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT id, name, category
                FROM places
                WHERE region_id = ?
                  AND status = 'ACTIVE'
                  AND category <> 'SHELTER'
                  AND (visibility = 'PUBLIC' OR trip_id = ?)
                ORDER BY
                  CASE ?
                    WHEN 'N' THEN CASE WHEN category = 'ATTRACTION' AND COALESCE(indoor, FALSE) = FALSE THEN 0 ELSE 1 END
                    WHEN 'C' THEN CASE WHEN category IN ('CULTURE', 'SHOPPING', 'CAFE', 'RESTAURANT')
                                            OR COALESCE(indoor, FALSE) = TRUE THEN 0 ELSE 1 END
                    ELSE 1
                  END,
                  CASE ?
                    WHEN 'A' THEN CASE WHEN category IN ('ATTRACTION', 'SHOPPING')
                                            OR COALESCE(basic_info, '') LIKE '%\"pace\":\"ACTIVE\"%' THEN 0 ELSE 1 END
                    WHEN 'R' THEN CASE WHEN category IN ('CULTURE', 'CAFE', 'RESTAURANT', 'ACCOMMODATION')
                                            OR COALESCE(basic_info, '') LIKE '%\"pace\":\"RELAXED\"%' THEN 0 ELSE 1 END
                    ELSE 1
                  END,
                  CASE WHEN ? = 'S' THEN id ELSE 0 END DESC,
                  id
                LIMIT ?
                """)
                .params(regionId, tripId,
                        String.valueOf(profile.place()),
                        String.valueOf(profile.energy()),
                        String.valueOf(profile.preparation()),
                        limit)
                .query().listOfRows();
        return rows.stream()
                .map(row -> new PlaceCandidate(
                        RowSupport.longValue(row, "id"),
                        RowSupport.strValue(row, "name"),
                        RowSupport.strValue(row, "category")
                ))
                .toList();
    }

    private List<PlannedItem> itemsForDay(long planId, LocalDate date, int dayIndex,
                                          List<PlaceCandidate> places, ProfileCode profile) {
        int count = Math.min(MAX_PLACES_PER_DAY, places.size());
        int startHour = profile.preparation() == 'P' ? 9 : 10;
        int startMinute = profile.preparation() == 'P' ? 30 : 0;
        int durationMinutes = profile.energy() == 'A' ? 90 : 120;
        int intervalMinutes = profile.energy() == 'A' ? 120 : 150;
        int offset = Math.floorMod(dayIndex * count, places.size());

        List<PlannedItem> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            PlaceCandidate place = places.get((offset + index) % places.size());
            LocalDateTime startsAt = date.atTime(startHour, startMinute)
                    .plusMinutes((long) index * intervalMinutes);
            result.add(new PlannedItem(
                    planId,
                    place.id(),
                    itemType(place.category()),
                    place.name(),
                    index + 1,
                    startsAt,
                    startsAt.plusMinutes(durationMinutes)
            ));
        }
        return result;
    }

    private void deleteItems(List<Long> planIds) {
        if (planIds.isEmpty()) return;
        String placeholders = String.join(", ", Collections.nCopies(planIds.size(), "?"));
        jdbc.sql("""
                UPDATE travel_routes
                SET from_plan_item_id = NULL, to_plan_item_id = NULL, status = 'EXPIRED',
                    updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (%s)
                """.formatted(placeholders))
                .params(planIds)
                .update();
        jdbc.sql("""
                UPDATE travel_supplies
                SET plan_item_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE plan_item_id IN (
                    SELECT id FROM travel_plan_items WHERE plan_id IN (%s)
                )
                """.formatted(placeholders))
                .params(planIds)
                .update();
        jdbc.sql("""
                UPDATE notifications
                SET plan_item_id = NULL
                WHERE plan_item_id IN (
                    SELECT id FROM travel_plan_items WHERE plan_id IN (%s)
                )
                """.formatted(placeholders))
                .params(planIds)
                .update();
        jdbc.sql("DELETE FROM travel_plan_items WHERE plan_id IN (" + placeholders + ")")
                .params(planIds)
                .update();
    }

    private void deletePlansAfterDay(long tripId, int lastDayNumber) {
        List<Long> planIds = jdbc.sql("""
                SELECT id FROM travel_plans
                WHERE trip_id = ? AND day_number > ?
                """)
                .params(tripId, lastDayNumber)
                .query(Long.class)
                .list();
        if (planIds.isEmpty()) return;
        String placeholders = String.join(", ", Collections.nCopies(planIds.size(), "?"));
        jdbc.sql("""
                UPDATE notifications SET route_id = NULL
                WHERE route_id IN (SELECT id FROM travel_routes WHERE plan_id IN (%s))
                """.formatted(placeholders)).params(planIds).update();
        jdbc.sql("""
                UPDATE notifications SET proposal_id = NULL
                WHERE proposal_id IN (
                    SELECT id FROM ai_schedule_change_proposals WHERE plan_id IN (%s)
                )
                """.formatted(placeholders)).params(planIds).update();
        jdbc.sql("UPDATE notifications SET plan_id = NULL WHERE plan_id IN (" + placeholders + ")")
                .params(planIds).update();
        jdbc.sql("DELETE FROM travel_plans WHERE id IN (" + placeholders + ")")
                .params(planIds).update();
    }

    private void insertItems(List<PlannedItem> items) {
        if (items.isEmpty()) return;
        String rowPlaceholder = "(?, ?, ?, ?, ?, ?, ?, 'PLANNED')";
        String values = String.join(", ", Collections.nCopies(items.size(), rowPlaceholder));
        List<Object> parameters = new ArrayList<>(items.size() * 7);
        for (PlannedItem item : items) {
            parameters.add(item.planId());
            parameters.add(item.placeId());
            parameters.add(item.itemType());
            parameters.add(item.title());
            parameters.add(item.sequenceNo());
            parameters.add(item.startsAt());
            parameters.add(item.endsAt());
        }
        jdbc.sql("""
                INSERT INTO travel_plan_items (plan_id, place_id, item_type, title, sequence_no,
                                               planned_start, planned_end, status)
                VALUES %s
                """.formatted(values))
                .params(parameters)
                .update();
    }

    private Map<Long, List<Map<String, Object>>> loadItems(List<Long> planIds) {
        String placeholders = String.join(", ", Collections.nCopies(planIds.size(), "?"));
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT i.plan_id, i.id, i.sequence_no, i.planned_start, i.planned_end,
                       i.status, i.item_type, i.title, i.description, i.estimated_cost, i.memo,
                       p.id AS place_id, p.name AS place_name, p.category, p.address,
                       p.latitude, p.longitude
                FROM travel_plan_items i
                LEFT JOIN places p ON p.id = i.place_id
                WHERE i.plan_id IN (%s)
                ORDER BY i.plan_id, i.sequence_no
                """.formatted(placeholders))
                .params(planIds)
                .query().listOfRows();

        Map<Long, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long planId = RowSupport.longValue(row, "plan_id");
            Map<String, Object> item = new LinkedHashMap<>(row);
            removeCaseInsensitive(item, "plan_id");
            result.computeIfAbsent(planId, ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static void removeCaseInsensitive(Map<String, Object> map, String key) {
        map.keySet().removeIf(candidate -> candidate.equalsIgnoreCase(key));
    }

    private static String itemType(String category) {
        return switch (category) {
            case "RESTAURANT", "CAFE" -> "MEAL";
            case "ACCOMMODATION" -> "ACCOMMODATION";
            default -> "PLACE";
        };
    }

    private LocalDate tripDate(Map<String, Object> trip, String column) {
        Object raw = RowSupport.value(trip, column);
        if (raw instanceof LocalDate date) return date;
        if (raw instanceof java.sql.Date date) return date.toLocalDate();
        if (raw instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        return LocalDate.parse(raw.toString().substring(0, 10));
    }

    private record PlaceCandidate(long id, String name, String category) {
    }

    private record PlannedItem(
            long planId,
            long placeId,
            String itemType,
            String title,
            int sequenceNo,
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {
    }

    private record ProfileCode(char preparation, char place, char energy) {
        private static ProfileCode from(String code) {
            String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (!normalized.matches("[PS][NC][AR]")) {
                throw new ApiException(HttpStatus.CONFLICT, "일정에 반영할 성향 검사 결과가 올바르지 않습니다.");
            }
            return new ProfileCode(normalized.charAt(0), normalized.charAt(1), normalized.charAt(2));
        }
    }
}
