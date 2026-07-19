package com.gayadi.server.event;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
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
import java.util.UUID;

@Service
public class EventService {
    private static final String SHELTER_PLACE_ID = "20000000-0000-0000-0000-000000000004";

    private final JdbcClient jdbc;
    private final TripService trips;
    private final PlanService plans;
    private final JsonSupport json;

    public EventService(JdbcClient jdbc, TripService trips, PlanService plans, JsonSupport json) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.plans = plans;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> observe(String tripId, Observation command) {
        trips.requireTrip(tripId);
        Map<String, Object> plan = plans.get(tripId);
        String planId = PlanService.value(plan, "id").toString();
        int revision = ((Number) PlanService.value(plan, "revision_no")).intValue();
        String eventId = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO event_observations(id, place_id, event_type, source, observed_at, valid_to, severity, normalized_value)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(eventId, command.placeId(), command.eventType(), command.source(), LocalDateTime.now(),
                        LocalDateTime.now().plusHours(2), command.severity().name(), json.write(command.values())).update();
        if (command.severity() == Severity.LOW) {
            return Map.of("eventId", eventId, "impact", false, "message", "일정 변경이 필요하지 않습니다.");
        }
        String proposalId = UUID.randomUUID().toString();
        Map<String, Object> option = Map.of("key", "INDOOR_SHELTER", "placeId", SHELTER_PLACE_ID,
                "description", "가까운 실내 대피 장소로 다음 일정을 변경합니다.");
        jdbc.sql("""
                INSERT INTO change_proposals(id, trip_id, plan_id, event_id, base_revision_no,
                                             reason, options, before_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """).params(proposalId, tripId, planId, eventId, revision,
                        reason(command), json.write(List.of(option)), json.write(plan)).update();
        return proposal(proposalId);
    }

    public List<Map<String, Object>> proposals(String tripId) {
        trips.requireTrip(tripId);
        return jdbc.sql("SELECT * FROM change_proposals WHERE trip_id = ? ORDER BY created_at DESC")
                .param(tripId).query().listOfRows();
    }

    @Transactional
    public Map<String, Object> decide(String tripId, String proposalId, Decision command) {
        trips.requireMember(tripId, command.decidedBy());
        Map<String, Object> proposal = proposal(proposalId);
        if (!tripId.equals(PlanService.value(proposal, "trip_id").toString())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "해당 여행의 변경 제안이 아닙니다.");
        }
        if (!"PENDING".equals(PlanService.value(proposal, "status").toString())) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 변경 제안입니다.");
        }
        int baseRevision = ((Number) PlanService.value(proposal, "base_revision_no")).intValue();
        if (baseRevision != command.baseRevisionNo()) {
            throw new ApiException(HttpStatus.CONFLICT, "요청한 일정 버전이 변경 제안과 다릅니다.");
        }
        if (!command.approve()) {
            jdbc.sql("""
                    UPDATE change_proposals SET status = 'REJECTED', decided_by = ?, decided_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """).params(command.decidedBy(), proposalId).update();
            return proposal(proposalId);
        }
        String planId = PlanService.value(proposal, "plan_id").toString();
        int updated = jdbc.sql("""
                UPDATE trip_plans SET revision_no = revision_no + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND revision_no = ?
                """).params(planId, baseRevision).update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "TRIP_REVISION_CONFLICT: 일정이 이미 변경되었습니다.");
        }
        jdbc.sql("""
                UPDATE trip_plan_items SET place_id = ?
                WHERE id = (SELECT id FROM trip_plan_items
                            WHERE plan_id = ? AND status = 'PLANNED'
                            ORDER BY sequence_no LIMIT 1)
                """).params(SHELTER_PLACE_ID, planId).update();
        Map<String, Object> after = plans.get(tripId);
        jdbc.sql("""
                UPDATE change_proposals SET status = 'APPROVED', selected_option = ?, after_snapshot = ?,
                  decided_by = ?, decided_at = CURRENT_TIMESTAMP WHERE id = ?
                """).params(json.write(Map.of("key", command.selectedOptionKey())), json.write(after),
                        command.decidedBy(), proposalId).update();
        return proposal(proposalId);
    }

    private Map<String, Object> proposal(String id) {
        return new LinkedHashMap<>(jdbc.sql("SELECT * FROM change_proposals WHERE id = ?")
                .param(id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "변경 제안을 찾을 수 없습니다.")));
    }

    private String reason(Observation command) {
        return command.eventType() + " 이벤트가 " + command.severity() + " 단계로 관측되었습니다.";
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public record Observation(String placeId, String eventType, String source, Severity severity,
                              Map<String, Object> values) {
    }

    public record Decision(boolean approve, String selectedOptionKey, int baseRevisionNo, String decidedBy) {
    }
}
