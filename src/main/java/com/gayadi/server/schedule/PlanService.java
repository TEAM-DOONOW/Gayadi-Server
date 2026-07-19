package com.gayadi.server.schedule;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PlanService {
    private final JdbcClient jdbc;
    private final TripService trips;
    private final SurveyService surveys;
    private final JsonSupport json;

    public PlanService(JdbcClient jdbc, TripService trips, SurveyService surveys, JsonSupport json) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.surveys = surveys;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> generate(String tripId) {
        Map<String, Object> trip = trips.requireTrip(tripId);
        String status = value(trip, "status").toString();
        if (!List.of("DRAFT", "READY").contains(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 시작 전 일정만 다시 생성할 수 있습니다.");
        }
        Map<String, Object> profile = surveys.groupProfile(tripId);
        String responseId = surveys.latestResponseId(tripId);
        String planId = jdbc.sql("SELECT id FROM trip_plans WHERE trip_id = ?")
                .param(tripId).query(String.class).optional().orElse(null);
        if (planId == null) {
            planId = UUID.randomUUID().toString();
            jdbc.sql("""
                    INSERT INTO trip_plans(id, trip_id, survey_response_id, revision_no, preference_snapshot)
                    VALUES (?, ?, ?, 1, ?)
                    """).params(planId, tripId, responseId, json.write(profile)).update();
        } else {
            jdbc.sql("DELETE FROM trip_plan_items WHERE plan_id = ?").param(planId).update();
            jdbc.sql("""
                    UPDATE trip_plans SET survey_response_id = ?, revision_no = revision_no + 1,
                    preference_snapshot = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                    """).params(responseId, json.write(profile), planId).update();
        }
        String dominant = profile.get("dominantProfile").toString();
        List<String> placeIds = orderedPlaces(dominant);
        LocalDateTime start = toLocalDateTime(value(trip, "departure_at"));
        for (int i = 0; i < placeIds.size(); i++) {
            LocalDateTime itemStart = start.plusMinutes(i * 120L);
            jdbc.sql("""
                    INSERT INTO trip_plan_items(id, plan_id, place_id, sequence_no, planned_start, planned_end)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """).params(UUID.randomUUID().toString(), planId, placeIds.get(i), i + 1,
                            itemStart, itemStart.plusMinutes(90)).update();
        }
        trips.markReady(tripId);
        return get(tripId);
    }

    public Map<String, Object> get(String tripId) {
        trips.requireTrip(tripId);
        Map<String, Object> plan = new LinkedHashMap<>(jdbc.sql("SELECT * FROM trip_plans WHERE trip_id = ?")
                .param(tripId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "생성된 일정이 없습니다.")));
        String planId = value(plan, "id").toString();
        List<Map<String, Object>> items = jdbc.sql("""
                SELECT i.id, i.sequence_no, i.planned_start, i.planned_end, i.status,
                       p.id AS place_id, p.name AS place_name, p.category, p.address,
                       p.latitude, p.longitude
                FROM trip_plan_items i JOIN places p ON p.id = i.place_id
                WHERE i.plan_id = ? ORDER BY i.sequence_no
                """).param(planId).query().listOfRows();
        plan.put("items", items);
        return plan;
    }

    public Map<String, Object> firstPlace(String tripId) {
        return boundaryPlace(tripId, "ASC");
    }

    public Map<String, Object> lastPlace(String tripId) {
        return boundaryPlace(tripId, "DESC");
    }

    public static Object value(Map<String, Object> row, String key) {
        Object lower = row.get(key);
        return lower != null ? lower : row.get(key.toUpperCase());
    }

    private Map<String, Object> boundaryPlace(String tripId, String order) {
        String sql = """
                SELECT p.* FROM trip_plans tp
                JOIN trip_plan_items i ON i.plan_id = tp.id
                JOIN places p ON p.id = i.place_id
                WHERE tp.trip_id = ? ORDER BY i.sequence_no %s LIMIT 1
                """.formatted(order);
        return jdbc.sql(sql).param(tripId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "경로 계산 전에 일정이 필요합니다."));
    }

    private List<String> orderedPlaces(String profile) {
        List<String> ids = new ArrayList<>();
        if ("INDOOR".equals(profile)) ids.add("20000000-0000-0000-0000-000000000002");
        else if ("FOODIE".equals(profile)) ids.add("20000000-0000-0000-0000-000000000003");
        else ids.add("20000000-0000-0000-0000-000000000001");
        for (String candidate : List.of(
                "20000000-0000-0000-0000-000000000003",
                "20000000-0000-0000-0000-000000000002",
                "20000000-0000-0000-0000-000000000001")) {
            if (!ids.contains(candidate)) ids.add(candidate);
        }
        return ids.subList(0, 3);
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }
}
