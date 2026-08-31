package com.gayadi.server.event;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.event.query.AlternativePlaceQueryResult;
import com.gayadi.server.event.query.ChangeProposalOptionQueryResult;
import com.gayadi.server.event.query.ChangeProposalQueryResult;
import com.gayadi.server.event.query.EventPlanQueryResult;
import com.gayadi.server.event.query.EventTripQueryResult;
import com.gayadi.server.travel.TripErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 현장 관측과 일정 변경 제안의 SQL 실행, 잠금 및 DB Row 매핑을 담당합니다. */
@Repository
public class EventRepository {

    private static final String PROPOSAL_COLUMNS = """
            id,
            trip_id,
            plan_id,
            event_id,
            proposal_type,
            reason,
            status,
            base_revision_no,
            options_snapshot,
            selected_option_key,
            decided_by,
            generated_at,
            decided_at,
            applied_at,
            before_snapshot,
            after_snapshot
            """;

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;
    private final JsonSupport json;

    public EventRepository(JdbcClient jdbc, KeyHelper keyHelper, JsonSupport json) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
        this.json = json;
    }

    /** 동시 변경을 막기 위해 여행 DB 행을 잠급니다. */
    public EventTripQueryResult lockInProgressTrip(long tripId) {
        Map<String, Object> row = jdbc.sql("""
                SELECT region_id, status
                FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
        EventTripQueryResult trip = new EventTripQueryResult(
                RowSupport.longValue(row, "region_id"),
                RowSupport.strValue(row, "status"));
        if (!"IN_PROGRESS".equals(trip.status())) {
            throw new BusinessException(EventErrorCode.EVENT_TRIP_NOT_IN_PROGRESS);
        }
        return trip;
    }

    /** 현재 계획 정보를 DB에서 조회합니다. */
    public EventPlanQueryResult findCurrentPlan(long tripId) {
        return jdbc.sql("""
                SELECT id, version
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
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapPlan)
                .orElseThrow(() -> new BusinessException(
                        EventErrorCode.CHANGE_PROPOSAL_TARGET_PLAN_NOT_FOUND));
    }

    /** 변경 충돌을 막기 위해 계획 DB 행을 잠급니다. */
    public EventPlanQueryResult lockPlan(long tripId, long planId) {
        return jdbc.sql("""
                SELECT id, version
                FROM travel_plans
                WHERE id = ? AND trip_id = ?
                FOR UPDATE
                """)
                .params(planId, tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapPlan)
                .orElseThrow(() -> new BusinessException(EventErrorCode.EVENT_PLAN_NOT_FOUND));
    }

    /** 관측 상황 정보를 DB에 저장합니다. */
    public long insertObservation(
            Long placeId,
            long regionId,
            String eventType,
            String source,
            LocalDateTime validTo,
            String severity,
            String normalizedData) {
        return keyHelper.insert("""
                INSERT INTO event_observations (
                    place_id,
                    region_id,
                    event_type,
                    source,
                    observed_at,
                    valid_to,
                    severity,
                    normalized_data)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?)
                """,
                placeId,
                regionId,
                eventType,
                source,
                validTo,
                severity,
                normalizedData);
    }

    /** 변경 제안 여행 상황과 변경 제안 데이터를 DB에 저장합니다. */
    public long insertProposal(
            long tripId,
            long planId,
            long eventId,
            String proposalType,
            String reason,
            Object before,
            LocalDateTime expiresAt,
            int baseRevision,
            List<ChangeProposalOptionQueryResult> options) {
        return keyHelper.insert("""
                INSERT INTO ai_schedule_change_proposals (
                    trip_id,
                    plan_id,
                    event_id,
                    proposal_type,
                    reason,
                    before_snapshot,
                    after_snapshot,
                    status,
                    generated_at,
                    expires_at,
                    base_revision_no,
                    options_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, NULL, 'PENDING', CURRENT_TIMESTAMP, ?, ?, ?)
                """,
                tripId,
                planId,
                eventId,
                proposalType,
                reason,
                json.write(before),
                expiresAt,
                baseRevision,
                json.write(options));
    }

    /** 대기 중 AI 변경 제안 상태를 만료하거나 해제합니다. */
    public void expirePendingAgentProposals(
            long tripId,
            long planId,
            int baseRevision,
            String source) {
        jdbc.sql("""
                UPDATE ai_schedule_change_proposals
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND plan_id = ? AND base_revision_no = ?
                  AND status = 'PENDING'
                  AND event_id IN (
                    SELECT id FROM event_observations WHERE source = ?
                  )
                """)
                .params(tripId, planId, baseRevision, source)
                .update();
    }

    /** 전체 조건에 맞는 여행 상황과 변경 제안 데이터를 DB에서 조회합니다. */
    public List<ChangeProposalQueryResult> findAll(
            long tripId,
            int limit,
            int offset) {
        return jdbc.sql("SELECT " + PROPOSAL_COLUMNS + """
                FROM ai_schedule_change_proposals
                WHERE trip_id = ?
                ORDER BY created_at DESC
                LIMIT ? OFFSET ?
                """)
                .params(tripId, limit, offset)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapProposal)
                .toList();
    }

    /** 대기 중 조건에 맞는 여행 상황과 변경 제안 데이터를 DB에서 조회합니다. */
    public List<ChangeProposalQueryResult> findPending(long tripId, int limit) {
        return jdbc.sql("SELECT " + PROPOSAL_COLUMNS + """
                FROM ai_schedule_change_proposals
                WHERE trip_id = ? AND status = 'PENDING'
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """)
                .params(tripId, limit)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapProposal)
                .toList();
    }

    /** 변경 제안 조건에 맞는 여행 상황과 변경 제안 데이터를 DB에서 조회합니다. */
    public ChangeProposalQueryResult findProposal(long proposalId) {
        return jdbc.sql("SELECT " + PROPOSAL_COLUMNS + """
                FROM ai_schedule_change_proposals
                WHERE id = ?
                """)
                .param(proposalId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapProposal)
                .orElseThrow(() -> new BusinessException(EventErrorCode.CHANGE_PROPOSAL_NOT_FOUND));
    }

    /** 변경 충돌을 막기 위해 변경 제안 DB 행을 잠급니다. */
    public ChangeProposalQueryResult lockProposal(long tripId, long proposalId) {
        return jdbc.sql("SELECT " + PROPOSAL_COLUMNS + """
                FROM ai_schedule_change_proposals
                WHERE id = ? AND trip_id = ?
                FOR UPDATE
                """)
                .params(proposalId, tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapProposal)
                .orElseThrow(() -> new BusinessException(EventErrorCode.CHANGE_PROPOSAL_NOT_FOUND));
    }

    /** 여행 상황과 변경 제안 상태나 값을 DB에 반영합니다. */
    public boolean reject(long tripId, long proposalId, long decidedBy) {
        return jdbc.sql("""
                UPDATE ai_schedule_change_proposals
                SET status = 'REJECTED',
                    decided_by = ?,
                    decided_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND status = 'PENDING'
                """)
                .params(decidedBy, proposalId, tripId)
                .update() > 0;
    }

    /** 계획 버전 상태나 값을 DB에 반영합니다. */
    public boolean incrementPlanVersion(long tripId, long planId, int version) {
        return jdbc.sql("""
                UPDATE travel_plans
                SET version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND version = ?
                """)
                .params(planId, tripId, version)
                .update() > 0;
    }

    /** 다음 계획 항목 상태나 값을 DB에 반영합니다. */
    public boolean updateNextPlanItem(long planId, long placeId, String placeName) {
        return jdbc.sql("""
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
                .params(placeId, placeName, planId)
                .update() > 0;
    }

    /** 동일 상황의 다른 변경 제안 상태를 만료하거나 해제합니다. */
    public void expireSiblingProposals(
            long tripId,
            long planId,
            int baseRevision,
            long selectedProposalId) {
        jdbc.sql("""
                UPDATE ai_schedule_change_proposals
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND plan_id = ? AND base_revision_no = ?
                  AND id <> ? AND status = 'PENDING'
                """)
                .params(tripId, planId, baseRevision, selectedProposalId)
                .update();
    }

    /** 활성 경로 목록 여행 상황과 변경 제안 상태를 DB에서 만료 또는 해제합니다. */
    public void expireActiveRoutes(long tripId) {
        jdbc.sql("""
                UPDATE travel_routes
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .update();
    }

    /** 여행 상황과 변경 제안 상태나 값을 DB에 반영합니다. */
    public boolean approve(
            long tripId,
            long proposalId,
            String selectedOptionKey,
            long decidedBy,
            Object after) {
        return jdbc.sql("""
                UPDATE ai_schedule_change_proposals
                SET status = 'APPROVED',
                    after_snapshot = ?,
                    selected_option_key = ?,
                    decided_by = ?,
                    decided_at = CURRENT_TIMESTAMP,
                    applied_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND status = 'PENDING'
                """)
                .params(json.write(after), selectedOptionKey, decidedBy, proposalId, tripId)
                .update() > 0;
    }

    /** 실내 대피 대체 장소 정보를 DB에서 조회합니다. */
    public List<AlternativePlaceQueryResult> findShelterAlternatives(
            long tripId,
            long regionId,
            Long observedPlaceId) {
        return jdbc.sql("""
                SELECT candidate.id, candidate.name
                FROM places candidate
                LEFT JOIN places observed
                  ON observed.id = ? AND observed.region_id = candidate.region_id
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
                .query()
                .listOfRows()
                .stream()
                .map(this::mapPlace)
                .toList();
    }

    /** 사용 가능한 장소 정보를 DB에서 조회합니다. */
    public AlternativePlaceQueryResult findAvailablePlace(
            long tripId,
            long regionId,
            long placeId,
            boolean requireIndoor) {
        String indoorCondition = requireIndoor
                ? " AND (p.category = 'SHELTER' OR p.indoor = TRUE)"
                : "";
        return jdbc.sql("""
                SELECT p.id, p.name
                FROM places p
                WHERE p.id = ? AND p.region_id = ? AND p.status = 'ACTIVE'
                  AND (p.visibility = 'PUBLIC' OR p.trip_id = ?)
                """ + indoorCondition)
                .params(placeId, regionId, tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapPlace)
                .orElse(null);
    }

    /** 활성 대체 장소 여부나 개수를 DB에서 확인합니다. */
    public boolean isActiveAlternative(long tripId, long placeId, boolean requireIndoor) {
        String indoorCondition = requireIndoor
                ? " AND (p.category = 'SHELTER' OR p.indoor = TRUE)"
                : "";
        long count = jdbc.sql("""
                SELECT COUNT(*)
                FROM places p
                JOIN trips t ON t.region_id = p.region_id
                WHERE t.id = ? AND t.deleted_at IS NULL
                  AND p.id = ? AND p.status = 'ACTIVE'
                  AND (p.visibility = 'PUBLIC' OR p.trip_id = t.id)
                """ + indoorCondition)
                .params(tripId, placeId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return count > 0;
    }

    /** 장소 지역 여부나 개수를 DB에서 확인합니다. */
    public boolean isPlaceInRegion(long placeId, long regionId) {
        long count = jdbc.sql("""
                SELECT COUNT(*)
                FROM places
                WHERE id = ? AND region_id = ?
                """)
                .params(placeId, regionId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return count > 0;
    }

    private EventPlanQueryResult mapPlan(Map<String, Object> row) {
        return new EventPlanQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.intValue(row, "version"));
    }

    private AlternativePlaceQueryResult mapPlace(Map<String, Object> row) {
        return new AlternativePlaceQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "name"));
    }

    private ChangeProposalQueryResult mapProposal(Map<String, Object> row) {
        return new ChangeProposalQueryResult(
                RowSupport.longValue(row, "id"),
                longOrNull(row, "trip_id"),
                longOrNull(row, "plan_id"),
                longOrNull(row, "event_id"),
                textOrNull(row, "proposal_type"),
                textOrNull(row, "reason"),
                textOrNull(row, "status"),
                intOrNull(row, "base_revision_no"),
                options(row, "options_snapshot"),
                textOrNull(row, "selected_option_key"),
                longOrNull(row, "decided_by"),
                dateTimeOrNull(row, "generated_at"),
                dateTimeOrNull(row, "decided_at"),
                dateTimeOrNull(row, "applied_at"),
                jsonOrNull(row, "before_snapshot"),
                jsonOrNull(row, "after_snapshot"));
    }

    private List<ChangeProposalOptionQueryResult> options(
            Map<String, Object> row,
            String key) {
        Object value = raw(row, key);
        if (value == null || value.toString().isBlank()) {
            return List.of();
        }
        Object parsed = json.read(value.toString(), Object.class);
        if (!(parsed instanceof List<?> values)) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_OPTIONS_INVALID);
        }
        return values.stream()
                .map(this::mapOption)
                .toList();
    }

    private ChangeProposalOptionQueryResult mapOption(Object value) {
        if (!(value instanceof Map<?, ?> option)) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_OPTIONS_INVALID);
        }
        Object key = option.get("key");
        Object placeId = option.get("placeId");
        Object placeName = option.get("placeName");
        if (key == null || placeId == null || placeName == null) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_OPTIONS_INVALID);
        }
        Object requireIndoor = option.get("requireIndoor");
        return new ChangeProposalOptionQueryResult(
                key.toString(),
                number(placeId).longValue(),
                placeName.toString(),
                option.get("description") == null ? null : option.get("description").toString(),
                requireIndoor == null || Boolean.parseBoolean(requireIndoor.toString()));
    }

    private Object jsonOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null || value.toString().isBlank()
                ? null
                : json.read(value.toString(), Object.class);
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String textOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value.toString();
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : number(value).longValue();
    }

    private Integer intOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : number(value).intValue();
    }

    private LocalDateTime dateTimeOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }

    private Number number(Object value) {
        return value instanceof Number number
                ? number
                : Long.parseLong(value.toString());
    }
}
