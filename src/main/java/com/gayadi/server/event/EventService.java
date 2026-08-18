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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EventService {

    private static final String PRIMARY_SHELTER_OPTION = "INDOOR_SHELTER";

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
        Map<String, Object> trip = lockInProgressTrip(tripId);
        validateObservation(command);
        long regionId = RowSupport.longValue(trip, "region_id");
        requirePlaceInRegion(command.placeId(), regionId);
        Map<String, Object> planSnapshot = plans.get(tripId);
        Map<String, Object> targetPlan = currentPlan(tripId);
        long planId = RowSupport.longValue(targetPlan, "id");
        int baseRevision = RowSupport.intValue(targetPlan, "version");

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

        List<Map<String, Object>> options = shelterOptions(tripId, regionId, command.placeId());
        if (options.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "해당 지역에 이용할 수 있는 실내 대체 장소가 없습니다.");
        }

        long proposalId = keyHelper.insert("""
                INSERT INTO ai_schedule_change_proposals
                       (trip_id, plan_id, event_id, proposal_type, reason,
                        before_snapshot, after_snapshot, status, generated_at,
                        base_revision_no, options_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, NULL, 'PENDING', CURRENT_TIMESTAMP, ?, ?)
                """,
                tripId, planId, eventId, proposalType(command.eventType()),
                reason(command), json.write(planSnapshot), baseRevision, json.write(options));

        return proposal(proposalId);
    }

    public List<Map<String, Object>> proposals(long tripId) {
        return proposals(tripId, 100, 0);
    }

    public List<Map<String, Object>> proposals(long tripId, int requestedLimit, int requestedOffset) {
        trips.requireTrip(tripId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        int offset = Math.max(0, requestedOffset);
        return jdbc.sql("""
                SELECT *
                FROM ai_schedule_change_proposals
                WHERE trip_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """)
                .params(tripId, limit, offset)
                .query().listOfRows().stream()
                .map(this::withOptions)
                .toList();
    }

    public List<Map<String, Object>> pendingProposals(long tripId, int requestedLimit) {
        trips.requireTrip(tripId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return jdbc.sql("""
                SELECT id, proposal_type, reason, status, base_revision_no,
                       options_snapshot, generated_at
                FROM ai_schedule_change_proposals
                WHERE trip_id = ? AND status = 'PENDING'
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """)
                .params(tripId, limit)
                .query().listOfRows().stream()
                .map(this::withOptions)
                .toList();
    }

    @Transactional
    public Map<String, Object> decide(long tripId, long proposalId, Decision command) {
        trips.requireMember(tripId, command.decidedBy());
        lockInProgressTrip(tripId);
        Map<String, Object> proposal = lockedProposal(tripId, proposalId);
        if (!"PENDING".equals(RowSupport.strValue(proposal, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 변경 제안입니다.");
        }

        int proposalRevision = RowSupport.intValue(proposal, "base_revision_no");
        if (command.baseRevisionNo() != proposalRevision) {
            throw new ApiException(HttpStatus.CONFLICT, "변경 제안의 일정 버전과 요청한 버전이 다릅니다.");
        }

        if (!command.approve()) {
            int rejected = jdbc.sql("""
                    UPDATE ai_schedule_change_proposals
                    SET status = 'REJECTED', decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND trip_id = ? AND status = 'PENDING'
                    """)
                    .params(command.decidedBy(), proposalId, tripId)
                    .update();
            if (rejected == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 변경 제안입니다.");
            }
            return proposal(proposalId);
        }

        SelectedOption selected = selectedOption(proposal, command.selectedOptionKey());
        ensureActiveShelter(tripId, selected.placeId());

        long planId = RowSupport.longValue(proposal, "plan_id");
        Map<String, Object> currentPlan = lockedPlan(tripId, planId);
        int currentVersion = RowSupport.intValue(currentPlan, "version");
        if (currentVersion != proposalRevision) {
            throw new ApiException(HttpStatus.CONFLICT, "일정이 이미 변경되었습니다.");
        }

        int planUpdated = jdbc.sql("""
                UPDATE travel_plans
                SET version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND version = ?
                """)
                .params(planId, tripId, proposalRevision)
                .update();
        if (planUpdated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "일정이 이미 변경되었습니다.");
        }

        int itemUpdated = jdbc.sql("""
                UPDATE travel_plan_items
                SET place_id = ?, title = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = (
                    SELECT id
                    FROM travel_plan_items
                    WHERE plan_id = ? AND status = 'PLANNED'
                      AND (planned_start IS NULL OR planned_start >= CURRENT_TIMESTAMP)
                    ORDER BY sequence_no
                    LIMIT 1
                )
                """)
                .params(selected.placeId(), selected.placeName(), planId)
                .update();
        if (itemUpdated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "변경할 수 있는 예정 일정이 없습니다.");
        }

        // 장소가 바뀐 뒤에는 이전 동선과 선택 경로를 더 이상 사용할 수 없다.
        jdbc.sql("""
                UPDATE travel_routes
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .update();

        Map<String, Object> after = plans.get(tripId);
        int proposalUpdated = jdbc.sql("""
                UPDATE ai_schedule_change_proposals
                SET status = 'APPROVED', after_snapshot = ?, selected_option_key = ?,
                    decided_by = ?, decided_at = CURRENT_TIMESTAMP,
                    applied_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND status = 'PENDING'
                """)
                .params(json.write(after), selected.key(), command.decidedBy(), proposalId, tripId)
                .update();
        if (proposalUpdated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 변경 제안입니다.");
        }

        return proposal(proposalId);
    }

    private Map<String, Object> currentPlan(long tripId) {
        return jdbc.sql("""
                SELECT id, version, plan_date, day_number
                FROM travel_plans
                WHERE trip_id = ?
                ORDER BY CASE
                           WHEN plan_date = CURRENT_DATE THEN 0
                           WHEN plan_date > CURRENT_DATE THEN 1
                           ELSE 2
                         END,
                         CASE WHEN plan_date >= CURRENT_DATE THEN plan_date END ASC,
                         plan_date DESC,
                         day_number
                LIMIT 1
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "변경 제안을 만들 일정이 없습니다."));
    }

    private Map<String, Object> lockInProgressTrip(long tripId) {
        Map<String, Object> trip = jdbc.sql("""
                SELECT * FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
        if (!"IN_PROGRESS".equals(RowSupport.strValue(trip, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 중에만 현장 상황을 처리할 수 있습니다.");
        }
        return trip;
    }

    private List<Map<String, Object>> shelterOptions(long tripId, long regionId, Long observedPlaceId) {
        List<Map<String, Object>> places = jdbc.sql("""
                SELECT candidate.id, candidate.name, candidate.category
                FROM places candidate
                LEFT JOIN places observed ON observed.id = ? AND observed.region_id = candidate.region_id
                WHERE candidate.region_id = ? AND candidate.status = 'ACTIVE'
                  AND (candidate.category = 'SHELTER' OR candidate.indoor = TRUE)
                  AND (candidate.visibility = 'PUBLIC' OR candidate.trip_id = ?)
                ORDER BY
                  CASE WHEN candidate.category = 'SHELTER' THEN 0 ELSE 1 END,
                  CASE WHEN observed.id IS NULL THEN 0 ELSE
                    ((candidate.latitude - observed.latitude) * (candidate.latitude - observed.latitude)
                    + (candidate.longitude - observed.longitude) * (candidate.longitude - observed.longitude))
                  END,
                  candidate.id
                LIMIT 5
                """)
                .params(observedPlaceId, regionId, tripId)
                .query().listOfRows();

        List<Map<String, Object>> options = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            Map<String, Object> place = places.get(index);
            long placeId = RowSupport.longValue(place, "id");
            String placeName = RowSupport.strValue(place, "name");
            Map<String, Object> option = new LinkedHashMap<>();
            option.put("key", index == 0 ? PRIMARY_SHELTER_OPTION : PRIMARY_SHELTER_OPTION + "_" + placeId);
            option.put("placeId", placeId);
            option.put("placeName", placeName);
            option.put("description", placeName + "으로 다음 일정을 변경합니다.");
            options.add(option);
        }
        return options;
    }

    private Map<String, Object> lockedProposal(long tripId, long proposalId) {
        return jdbc.sql("""
                SELECT *
                FROM ai_schedule_change_proposals
                WHERE id = ? AND trip_id = ?
                FOR UPDATE
                """)
                .params(proposalId, tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "해당 여행의 변경 제안을 찾을 수 없습니다."));
    }

    private Map<String, Object> lockedPlan(long tripId, long planId) {
        return jdbc.sql("""
                SELECT id, version
                FROM travel_plans
                WHERE id = ? AND trip_id = ?
                FOR UPDATE
                """)
                .params(planId, tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "변경할 일정을 찾을 수 없습니다."));
    }

    private void ensureActiveShelter(long tripId, long placeId) {
        long count = jdbc.sql("""
                SELECT COUNT(*)
                FROM places p
                JOIN trips t ON t.region_id = p.region_id
                WHERE t.id = ? AND t.deleted_at IS NULL
                  AND p.id = ? AND p.status = 'ACTIVE'
                  AND (p.category = 'SHELTER' OR p.indoor = TRUE)
                  AND (p.visibility = 'PUBLIC' OR p.trip_id = t.id)
                """)
                .params(tripId, placeId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "선택한 실내 대체 장소를 더 이상 이용할 수 없습니다.");
        }
    }

    private void requirePlaceInRegion(Long placeId, long regionId) {
        if (placeId == null) return;
        long count = jdbc.sql("SELECT COUNT(*) FROM places WHERE id = ? AND region_id = ?")
                .params(placeId, regionId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "관측 장소가 여행 지역에 속하지 않습니다.");
        }
    }

    private SelectedOption selectedOption(Map<String, Object> proposal, String selectedKey) {
        if (selectedKey == null || selectedKey.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "승인할 대체 장소를 선택해야 합니다.");
        }
        Object rawOptions = valueOrNull(proposal, "options_snapshot");
        if (rawOptions == null || rawOptions.toString().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "변경 제안의 대체 장소 정보가 없습니다.");
        }

        Object parsed = json.read(rawOptions.toString(), Object.class);
        if (!(parsed instanceof List<?> options)) {
            throw new ApiException(HttpStatus.CONFLICT, "변경 제안의 대체 장소 정보가 올바르지 않습니다.");
        }
        for (Object value : options) {
            if (!(value instanceof Map<?, ?> option)) continue;
            Object key = option.get("key");
            if (key == null || !selectedKey.equals(key.toString())) continue;
            Object placeId = option.get("placeId");
            Object placeName = option.get("placeName");
            if (placeId == null || placeName == null) {
                throw new ApiException(HttpStatus.CONFLICT, "변경 제안의 대체 장소 정보가 올바르지 않습니다.");
            }
            return new SelectedOption(selectedKey, number(placeId).longValue(), placeName.toString());
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "변경 제안에 포함된 대체 장소만 선택할 수 있습니다.");
    }

    private Map<String, Object> proposal(long id) {
        Map<String, Object> row = jdbc.sql("SELECT * FROM ai_schedule_change_proposals WHERE id = ?")
                .param(id)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "변경 제안을 찾을 수 없습니다."));
        return withOptions(row);
    }

    private Map<String, Object> withOptions(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", RowSupport.longValue(row, "id"));
        putIfPresent(result, "tripId", row, "trip_id");
        putIfPresent(result, "planId", row, "plan_id");
        putIfPresent(result, "eventId", row, "event_id");
        putIfPresent(result, "type", row, "proposal_type");
        putIfPresent(result, "reason", row, "reason");
        putIfPresent(result, "status", row, "status");
        putIfPresent(result, "baseRevisionNo", row, "base_revision_no");
        putIfPresent(result, "selectedOptionKey", row, "selected_option_key");
        putIfPresent(result, "decidedBy", row, "decided_by");
        putIfPresent(result, "generatedAt", row, "generated_at");
        putIfPresent(result, "decidedAt", row, "decided_at");
        putIfPresent(result, "appliedAt", row, "applied_at");
        putJsonIfPresent(result, "before", row, "before_snapshot");
        putJsonIfPresent(result, "after", row, "after_snapshot");
        Object raw = valueOrNull(row, "options_snapshot");
        result.put("options", raw == null || raw.toString().isBlank()
                ? List.of()
                : json.read(raw.toString(), Object.class));
        return result;
    }

    private void putIfPresent(
            Map<String, Object> target, String targetKey,
            Map<String, Object> row, String rowKey) {
        Object value = valueOrNull(row, rowKey);
        if (value != null) target.put(targetKey, value);
    }

    private void putJsonIfPresent(
            Map<String, Object> target, String targetKey,
            Map<String, Object> row, String rowKey) {
        Object value = valueOrNull(row, rowKey);
        if (value != null && !value.toString().isBlank()) {
            target.put(targetKey, json.read(value.toString(), Object.class));
        }
    }

    private String reason(Observation command) {
        return eventLabel(command.eventType()) + " 상황이 " + severityLabel(command.severity()) + " 단계로 확인되었습니다.";
    }

    private void validateObservation(Observation command) {
        if (!List.of("WEATHER", "CONGESTION", "TRANSPORT", "CLOSURE", "DISASTER")
                .contains(command.eventType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "올바르지 않은 현장 상황 종류입니다.");
        }
        if (command.source().length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "현장 상황 출처는 50자까지 입력할 수 있습니다.");
        }
    }

    private String proposalType(String eventType) {
        return switch (eventType) {
            case "WEATHER" -> "WEATHER_CHANGE";
            case "CONGESTION" -> "CONGESTION_CHANGE";
            case "TRANSPORT" -> "TRANSPORT_CHANGE";
            default -> "USER_REQUEST";
        };
    }

    private String eventLabel(String eventType) {
        return switch (eventType) {
            case "WEATHER" -> "날씨";
            case "CONGESTION" -> "혼잡";
            case "TRANSPORT" -> "교통";
            case "CLOSURE" -> "운영 중단";
            case "DISASTER" -> "재난";
            default -> "현장";
        };
    }

    private String severityLabel(Severity severity) {
        return switch (severity) {
            case LOW -> "낮음";
            case MEDIUM -> "보통";
            case HIGH -> "높음";
            case CRITICAL -> "매우 높음";
        };
    }

    private static Object valueOrNull(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    private static Number number(Object value) {
        if (value instanceof Number number) return number;
        return Long.parseLong(value.toString());
    }

    private record SelectedOption(String key, long placeId, String placeName) {
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
