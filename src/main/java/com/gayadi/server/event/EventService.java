package com.gayadi.server.event;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    private static final long SHELTER_PLACE_ID = 4L;

    private final JdbcClient jdbc;
    private final TripService trips;
    private final PlanService plans;
    private final JsonSupport json;
    private final KeyHelper keyHelper;

    public EventService(JdbcClient jdbc, TripService trips, PlanService plans, JsonSupport json, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.plans = plans;
        this.json = json;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public Map<String, Object> observe(long tripId, Observation command) {
        Map<String, Object> trip = trips.requireTrip(tripId);
        long regionId = RowSupport.longValue(trip, "region_id");
        Map<String, Object> plan = plans.get(tripId);
        long planId = RowSupport.longValue(plan, "id");

        long eventId = keyHelper.insert("""
                INSERT INTO event_observations (place_id, region_id, event_type, source,
                                                 observed_at, valid_to, severity, normalized_data)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?)
                """,
                command.placeId(), regionId, command.eventType(), command.source(),
                LocalDateTime.now().plusHours(2), command.severity().name(),
                json.write(command.values()));

        if (command.severity() == Severity.LOW) {
            return Map.of("eventId", eventId, "impact", false, "message", "일정 변경이 필요하지 않습니다.");
        }

        String proposalType = proposalType(command.eventType());
        Map<String, Object> option = Map.of(
                "key", "INDOOR_SHELTER",
                "placeId", SHELTER_PLACE_ID,
                "description", "가까운 실내 대피 장소로 다음 일정을 변경합니다."
        );

        long proposalId = keyHelper.insert("""
                INSERT INTO ai_schedule_change_proposals (trip_id, plan_id, event_id, proposal_type,
                                                           reason, before_snapshot, after_snapshot,
                                                           status, generated_at)
                VALUES (?, ?, ?, ?, ?, ?, NULL, 'PENDING', CURRENT_TIMESTAMP)
                """,
                tripId, planId, eventId, proposalType,
                reason(command), json.write(plan));

        return proposal(proposalId);
    }

    public List<Map<String, Object>> proposals(long tripId) {
        trips.requireTrip(tripId);
        return jdbc.sql(
                "SELECT * FROM ai_schedule_change_proposals WHERE trip_id = ? ORDER BY created_at DESC")
                .param(tripId)
                .query().listOfRows();
    }

    @Transactional
    public Map<String, Object> decide(long tripId, long proposalId, Decision command) {
        trips.requireMember(tripId, command.decidedBy());
        Map<String, Object> proposal = proposal(proposalId);

        if (tripId != RowSupport.longValue(proposal, "trip_id")) {
            throw new ApiException(HttpStatus.NOT_FOUND, "해당 여행의 변경 제안이 아닙니다.");
        }
        if (!"PENDING".equals(RowSupport.strValue(proposal, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 변경 제안입니다.");
        }

        Map<String, Object> currentPlan = plans.get(tripId);
        int currentVersion = RowSupport.intValue(currentPlan, "version");
        if (currentVersion != command.baseRevisionNo()) {
            throw new ApiException(HttpStatus.CONFLICT, "요청한 일정 버전이 변경 제안과 다릅니다.");
        }

        if (!command.approve()) {
            jdbc.sql("""
                    UPDATE ai_schedule_change_proposals
                    SET status = 'REJECTED', decided_by = ?, decided_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """)
                    .params(command.decidedBy(), proposalId)
                    .update();
            return proposal(proposalId);
        }

        long planId = RowSupport.longValue(proposal, "plan_id");
        int updated = jdbc.sql("""
                UPDATE travel_plans SET version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ?
                """)
                .params(planId, command.baseRevisionNo())
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "TRIP_REVISION_CONFLICT: 일정이 이미 변경되었습니다.");
        }

        jdbc.sql("""
                UPDATE travel_plan_items SET place_id = ?
                WHERE id = (SELECT id FROM travel_plan_items
                            WHERE plan_id = ? AND status = 'PLANNED'
                            ORDER BY sequence_no LIMIT 1)
                """)
                .params(SHELTER_PLACE_ID, planId)
                .update();

        Map<String, Object> after = plans.get(tripId);
        jdbc.sql("""
                UPDATE ai_schedule_change_proposals
                SET status = 'APPROVED', after_snapshot = ?,
                    decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                    applied_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .params(json.write(after), command.decidedBy(), proposalId)
                .update();

        return proposal(proposalId);
    }

    private Map<String, Object> proposal(long id) {
        return jdbc.sql("SELECT * FROM ai_schedule_change_proposals WHERE id = ?")
                .param(id)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "변경 제안을 찾을 수 없습니다."));
    }

    private String reason(Observation command) {
        return command.eventType() + " 이벤트가 " + command.severity() + " 단계로 관측되었습니다.";
    }

    private String proposalType(String eventType) {
        return switch (eventType) {
            case "WEATHER" -> "WEATHER_CHANGE";
            case "CONGESTION" -> "CONGESTION_CHANGE";
            case "TRANSPORT" -> "TRANSPORT_CHANGE";
            default -> "USER_REQUEST";
        };
    }

    public record Observation(
            Long placeId,
            String eventType,
            String source,
            Severity severity,
            Map<String, Object> values
    ) {
    }

    public record Decision(
            boolean approve,
            String selectedOptionKey,
            int baseRevisionNo,
            long decidedBy
    ) {
    }
}
