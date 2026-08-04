package com.gayadi.server.schedule;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PlanService {

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
        Map<String, Object> trip = trips.requireTrip(tripId);
        String status = RowSupport.strValue(trip, "status");
        if (!"PLANNING".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 시작 전 일정만 다시 생성할 수 있습니다.");
        }
        Map<String, Object> profile = surveys.groupProfile(tripId);
        long ownerId = RowSupport.longValue(trip, "owner_id");
        LocalDate tripDate = tripStartDate(trip);

        Long planId = jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? ORDER BY day_number LIMIT 1")
                .param(tripId)
                .query(Long.class)
                .optional()
                .orElse(null);

        if (planId == null) {
            planId = keyHelper.insert("""
                    INSERT INTO travel_plans (trip_id, plan_date, day_number, title, source_type,
                                              status, created_by, version, preference_snapshot)
                    VALUES (?, ?, 1, '1일차 일정', 'AI', 'DRAFT', ?, 0, ?)
                    """,
                    tripId, tripDate, ownerId, json.write(profile));
        } else {
            jdbc.sql("DELETE FROM travel_plan_items WHERE plan_id = ?").param(planId).update();
            jdbc.sql("""
                    UPDATE travel_plans SET version = version + 1, preference_snapshot = ?,
                                             updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """)
                    .params(json.write(profile), planId)
                    .update();
        }

        String dominant = RowSupport.strValue(profile, "dominantProfile");
        List<Long> placeIds = orderedPlaces(dominant);

        for (int i = 0; i < placeIds.size(); i++) {
            long placeId = placeIds.get(i);
            LocalDateTime itemStart = tripDate.atTime(10, 0).plusMinutes((long) i * 120);
            jdbc.sql("""
                    INSERT INTO travel_plan_items (plan_id, place_id, item_type, title, sequence_no,
                                                    planned_start, planned_end, status)
                    VALUES (?, ?, 'PLACE', ?, ?, ?, ?, 'PLANNED')
                    """)
                    .params(planId, placeId, "장소 " + (i + 1), i + 1, itemStart, itemStart.plusMinutes(90))
                    .update();
        }

        return get(tripId);
    }

    public Map<String, Object> get(long tripId) {
        trips.requireTrip(tripId);
        Map<String, Object> row = jdbc.sql("SELECT * FROM travel_plans WHERE trip_id = ? ORDER BY day_number LIMIT 1")
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "생성된 일정이 없습니다."));
        Map<String, Object> plan = new LinkedHashMap<>(row);
        long planId = RowSupport.longValue(plan, "id");
        List<Map<String, Object>> items = jdbc.sql("""
                SELECT i.id, i.sequence_no, i.planned_start, i.planned_end, i.status, i.item_type,
                       p.id AS place_id, p.name AS place_name, p.category, p.address,
                       p.latitude, p.longitude
                FROM travel_plan_items i
                JOIN places p ON p.id = i.place_id
                WHERE i.plan_id = ?
                ORDER BY i.sequence_no
                """)
                .param(planId)
                .query().listOfRows();
        plan.put("items", items);
        return plan;
    }

    public Map<String, Object> firstPlace(long tripId) {
        return boundaryPlace(tripId, "ASC");
    }

    public Map<String, Object> lastPlace(long tripId) {
        return boundaryPlace(tripId, "DESC");
    }

    private Map<String, Object> boundaryPlace(long tripId, String order) {
        String sql = """
                SELECT p.* FROM travel_plans tp
                JOIN travel_plan_items i ON i.plan_id = tp.id
                JOIN places p ON p.id = i.place_id
                WHERE tp.trip_id = ?
                ORDER BY i.sequence_no
                """ + order + " LIMIT 1";
        return jdbc.sql(sql)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "경로 계산 전에 일정이 필요합니다."));
    }

    private List<Long> orderedPlaces(String profile) {
        List<Long> ids = new ArrayList<>();
        switch (profile) {
            case "INDOOR" -> ids.add(2L);
            case "FOODIE" -> ids.add(3L);
            default -> ids.add(1L);
        }
        for (long candidate : new long[]{3L, 2L, 1L}) {
            if (!ids.contains(candidate)) ids.add(candidate);
        }
        return ids.subList(0, 3);
    }

    private LocalDate tripStartDate(Map<String, Object> trip) {
        Object raw = RowSupport.value(trip, "start_date");
        if (raw instanceof LocalDate ld) return ld;
        if (raw instanceof java.sql.Date sd) return sd.toLocalDate();
        if (raw instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        return LocalDate.parse(raw.toString().substring(0, 10));
    }
}
