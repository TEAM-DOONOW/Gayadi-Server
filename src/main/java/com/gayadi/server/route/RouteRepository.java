package com.gayadi.server.route;

import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.route.query.RouteLockQueryResult;
import com.gayadi.server.route.query.RouteMemberQueryResult;
import com.gayadi.server.route.query.RouteOptionQueryResult;
import com.gayadi.server.route.query.RoutePlaceQueryResult;
import com.gayadi.server.route.query.RouteQueryResult;
import com.gayadi.server.route.query.RouteTripQueryResult;
import com.gayadi.server.travel.model.DepartureMode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** 경로 추천·선택에 필요한 영속성 조회와 상태 변경을 담당합니다. */
@Repository
public class RouteRepository {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public RouteRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 계산된 경로와 요약 지표를 추천 상태로 저장합니다. */
    public long saveRecommendation(
            long planId,
            Long memberId,
            RoutePhase phase,
            String routeData,
            int durationMinutes,
            int transferCount,
            int fare) {
        return keyHelper.insert("""
                INSERT INTO travel_routes (plan_id, member_id, phase, route_data, transport_mode,
                                            duration_minutes, transfer_count, fare, status, recommended_at)
                VALUES (?, ?, ?, ?, 'PUBLIC_TRANSIT', ?, ?, ?, 'RECOMMENDED', CURRENT_TIMESTAMP)
                """,
                planId,
                memberId,
                phase.name(),
                routeData,
                durationMinutes,
                transferCount,
                fare);
    }

    /** 추천 경로를 사용자가 선택한 상태로 변경합니다. */
    public void select(long routeId) {
        jdbc.sql("""
                UPDATE travel_routes
                SET status = 'SELECTED', selected_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(routeId)
                .update();
    }

    /** 선택 경로 여행 경로 상태를 DB에서 만료 또는 해제합니다. */
    public void clearSelection(long planId, RoutePhase phase, Long memberId) {
        JdbcClient.StatementSpec statement = jdbc.sql(memberId == null ? """
                UPDATE travel_routes
                SET status = 'CANCELED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND phase = ? AND member_id IS NULL AND status = 'SELECTED'
                """ : """
                UPDATE travel_routes
                SET status = 'CANCELED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND phase = ? AND member_id = ? AND status = 'SELECTED'
                """);
        if (memberId == null) {
            statement
            .params(planId, phase.name())
            .update();
        } else {
            statement
            .params(planId, phase.name(), memberId)
            .update();
        }
    }

    /** 활성 여행 상태를 만료하거나 해제합니다. */
    public int expireActiveForTrip(long tripId) {
        return jdbc.sql("""
                UPDATE travel_routes
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .update();
    }

    /** 활성 여행 경로 상태를 DB에서 만료 또는 해제합니다. */
    public void expireActive(long planId, RoutePhase phase, Long memberId) {
        updateScopedStatus(planId, phase, memberId, null, "RECOMMENDED", "EXPIRED");
    }

    /** 선택 경로 목록 여행 경로 상태를 DB에서 만료 또는 해제합니다. */
    public void expireSelections(
            long planId,
            RoutePhase phase,
            Long memberId,
            long exceptRouteId) {
        updateScopedStatus(planId, phase, memberId, exceptRouteId, "SELECTED", "EXPIRED");
    }

    private void updateScopedStatus(
            long planId,
            RoutePhase phase,
            Long memberId,
            Long exceptRouteId,
            String currentStatus,
            String nextStatus) {
        String memberCondition = memberId == null ? "member_id IS NULL" : "member_id = ?";
        String exceptCondition = exceptRouteId == null ? "" : " AND id != ?";
        JdbcClient.StatementSpec statement = jdbc.sql("""
                UPDATE travel_routes SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND phase = ? AND %s AND status = ?%s
                """.formatted(memberCondition, exceptCondition));
        if (memberId == null && exceptRouteId == null) {
            statement
            .params(nextStatus, planId, phase.name(), currentStatus)
            .update();
        } else if (memberId == null) {
            statement
            .params(nextStatus, planId, phase.name(), currentStatus, exceptRouteId)
            .update();
        } else if (exceptRouteId == null) {
            statement
            .params(nextStatus, planId, phase.name(), memberId, currentStatus)
            .update();
        } else {
            statement
            .params(nextStatus, planId, phase.name(), memberId, currentStatus, exceptRouteId)
            .update();
        }
    }

    /** 선택 경로 목록 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public List<RouteQueryResult> findSelections(long tripId, long participantId) {
        return jdbc.sql("""
                SELECT r.id, r.plan_id, p.trip_id, r.member_id, r.phase, r.route_data,
                       r.transport_mode, r.duration_minutes, r.distance_meters,
                       r.transfer_count, r.fare, r.status, r.recommended_at, r.selected_at,
                       participant.user_id AS member_user_id
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                LEFT JOIN trip_participants participant ON participant.id = r.member_id
                WHERE p.trip_id = ? AND r.status = 'SELECTED'
                  AND (r.member_id IS NULL OR r.member_id = ?)
                ORDER BY r.selected_at DESC, r.id DESC
                """)
                .params(tripId, participantId)
                .query()
                .listOfRows()
                .stream()
                .map(this::route)
                .toList();
    }

    /** 일정 경유 장소 정보를 DB에서 조회합니다. */
    public List<RoutePlaceQueryResult> findItineraryStops(long tripId, int limit) {
        return jdbc.sql("""
                SELECT p.id, p.name, p.latitude, p.longitude
                FROM travel_plans tp
                JOIN travel_plan_items i ON i.plan_id = tp.id
                JOIN places p ON p.id = i.place_id AND p.status = 'ACTIVE'
                WHERE tp.trip_id = ? AND tp.status != 'CANCELED'
                ORDER BY tp.day_number, i.sequence_no, i.id
                LIMIT ?
                """)
                .params(tripId, limit)
                .query()
                .listOfRows()
                .stream()
                .map(this::place)
                .toList();
    }

    /** 일정 변경 버전 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public String findScheduleRevision(long tripId) {
        return jdbc.sql("""
                SELECT p.id AS plan_id, p.version, p.day_number,
                       i.id AS item_id, i.place_id, i.sequence_no,
                       place.name AS place_name, place.latitude, place.longitude,
                       place.updated_at AS place_updated_at
                FROM travel_plans p
                LEFT JOIN travel_plan_items i ON i.plan_id = p.id
                LEFT JOIN places place ON place.id = i.place_id
                WHERE p.trip_id = ? AND p.status != 'CANCELED'
                ORDER BY p.day_number, p.id, i.sequence_no, i.id
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .map(row -> revisionToken(
                        RowSupport.longValue(row, "plan_id"),
                        RowSupport.intValue(row, "version"),
                        RowSupport.intValue(row, "day_number"),
                        nullableValue(row, "item_id"),
                        nullableValue(row, "place_id"),
                        nullableValue(row, "sequence_no"),
                        nullableValue(row, "place_name"),
                        nullableValue(row, "latitude"),
                        nullableValue(row, "longitude"),
                        nullableValue(row, "place_updated_at")))
                .collect(Collectors.joining("|"));
    }

    /** 여행 변경 버전 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public String findTripRevision(long tripId) {
        return jdbc.sql("""
                SELECT departure_mode, meeting_place_id, version
                FROM trips WHERE id = ? AND deleted_at IS NULL
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(row -> revisionToken(
                        RowSupport.strValue(row, "departure_mode"),
                        nullableValue(row, "meeting_place_id"),
                        RowSupport.intValue(row, "version")))
                .orElse(null);
    }

    /** 참여자 변경 버전 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public String findMemberRevision(long memberId) {
        return jdbc.sql("""
                SELECT departure_place_id, return_place_id, updated_at
                FROM trip_participants WHERE id = ? AND status = 'JOINED'
                """)
                .param(memberId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(row -> revisionToken(
                        nullableValue(row, "departure_place_id"),
                        nullableValue(row, "return_place_id"),
                        nullableValue(row, "updated_at")))
                .orElse("MISSING");
    }

    /** 선택지 후보 목록 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public List<RouteOptionQueryResult> findOptionCandidates(
            long planId,
            RoutePhase phase,
            Long memberId) {
        JdbcClient.StatementSpec statement = jdbc.sql(memberId == null ? """
                SELECT id, route_data FROM travel_routes
                WHERE plan_id = ? AND phase = ? AND member_id IS NULL
                  AND status IN ('RECOMMENDED', 'SELECTED')
                ORDER BY id DESC
                """ : """
                SELECT id, route_data FROM travel_routes
                WHERE plan_id = ? AND phase = ? AND member_id = ?
                  AND status IN ('RECOMMENDED', 'SELECTED')
                ORDER BY id DESC
                """);
        List<Map<String, Object>> rows;
        if (memberId == null) {
            rows = statement
                    .params(
                            planId,
                            phase.name())
                    .query()
                    .listOfRows();
        } else {
            rows = statement
                    .params(
                            planId,
                            phase.name(),
                            memberId)
                    .query()
                    .listOfRows();
        }

        return rows.stream()
                .map(row -> new RouteOptionQueryResult(
                        RowSupport.longValue(row, "id"),
                        String.valueOf(nullableValue(row, "route_data"))))
                .toList();
    }

    /** 계획 식별자 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public Long findPlanId(long tripId, RoutePhase phase) {
        String order = phase == RoutePhase.RETURN ? "DESC" : "ASC";
        return jdbc.sql("""
                SELECT id FROM travel_plans
                WHERE trip_id = ? AND status != 'CANCELED'
                ORDER BY day_number %s LIMIT 1
                """.formatted(order))
                .param(tripId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    /** 변경 충돌을 막기 위해 여행 DB 행을 잠급니다. */
    public RouteTripQueryResult lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT * FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::trip)
                .orElse(null);
    }

    /** 변경 충돌을 막기 위해 계획 DB 행을 잠급니다. */
    public boolean lockPlan(long planId) {
        return jdbc.sql("SELECT id FROM travel_plans WHERE id = ? FOR UPDATE")
                .param(planId)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /** 경로 계획 식별자 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public Long findRoutePlanId(long tripId, long routeId) {
        return jdbc.sql("""
                SELECT r.plan_id
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                WHERE r.id = ? AND p.trip_id = ?
                """)
                .params(routeId, tripId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    /** 변경 충돌을 막기 위해 경로 DB 행을 잠급니다. */
    public RouteLockQueryResult lockRoute(long routeId, long planId) {
        return jdbc.sql("""
                SELECT id, plan_id, member_id, phase, status
                FROM travel_routes WHERE id = ? AND plan_id = ? FOR UPDATE
                """)
                .params(routeId, planId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::lockedRoute)
                .orElse(null);
    }

    /** 경로 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public RouteQueryResult findRoute(long tripId, long routeId) {
        return jdbc.sql("""
                SELECT r.id, r.plan_id, p.trip_id, r.member_id, r.phase, r.route_data,
                       r.transport_mode, r.duration_minutes, r.distance_meters,
                       r.transfer_count, r.fare, r.status, r.recommended_at, r.selected_at,
                       participant.user_id AS member_user_id
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                LEFT JOIN trip_participants participant ON participant.id = r.member_id
                WHERE r.id = ? AND p.trip_id = ?
                """)
                .params(routeId, tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::route)
                .orElse(null);
    }

    /** 참여자 식별자 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public Long findParticipantId(long tripId, long userId) {
        return jdbc.sql("""
                SELECT id FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    /** 참여자 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public RouteMemberQueryResult findMember(long tripId, long memberId) {
        return jdbc.sql("""
                SELECT id, user_id, departure_place_id, return_place_id
                FROM trip_participants
                WHERE trip_id = ? AND id = ? AND status = 'JOINED'
                """)
                .params(tripId, memberId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::member)
                .orElse(null);
    }

    /** 장소 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public RoutePlaceQueryResult findPlace(long placeId) {
        return jdbc.sql("""
                SELECT id, name, latitude, longitude
                FROM places WHERE id = ? AND status = 'ACTIVE'
                """)
                .param(placeId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::place)
                .orElse(null);
    }

    /** 사용자 식별자 조건에 맞는 여행 경로 데이터를 DB에서 조회합니다. */
    public Long findUserId(long participantId) {
        return jdbc.sql("SELECT user_id FROM trip_participants WHERE id = ?")
                .param(participantId)
                .query(Long.class)
                .optional()
                .orElse(null);
    }

    private RouteTripQueryResult trip(Map<String, Object> row) {
        return new RouteTripQueryResult(
                RowSupport.longValue(row, "id"),
                DepartureMode.valueOf(RowSupport.strValue(row, "departure_mode")),
                nullableLong(row, "meeting_place_id"));
    }

    private RouteMemberQueryResult member(Map<String, Object> row) {
        return new RouteMemberQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "user_id"),
                nullableLong(row, "departure_place_id"),
                nullableLong(row, "return_place_id"));
    }

    private RoutePlaceQueryResult place(Map<String, Object> row) {
        return new RoutePlaceQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "name"),
                ((Number) RowSupport.value(row, "latitude")).doubleValue(),
                ((Number) RowSupport.value(row, "longitude")).doubleValue());
    }

    private RouteLockQueryResult lockedRoute(Map<String, Object> row) {
        return new RouteLockQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "plan_id"),
                nullableLong(row, "member_id"),
                RoutePhase.valueOf(RowSupport.strValue(row, "phase")),
                RowSupport.strValue(row, "status"));
    }

    private RouteQueryResult route(Map<String, Object> row) {
        return new RouteQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "plan_id"),
                RowSupport.longValue(row, "trip_id"),
                nullableLong(row, "member_id"),
                nullableLong(row, "member_user_id"),
                RoutePhase.valueOf(RowSupport.strValue(row, "phase")),
                nullableText(row, "route_data"),
                RowSupport.strValue(row, "transport_mode"),
                nullableInteger(row, "duration_minutes"),
                nullableInteger(row, "distance_meters"),
                nullableInteger(row, "transfer_count"),
                nullableInteger(row, "fare"),
                RowSupport.strValue(row, "status"),
                nullableValue(row, "recommended_at") == null
                        ? null : AppDateFormat.databaseDateTime(nullableValue(row, "recommended_at")),
                nullableValue(row, "selected_at") == null
                        ? null : AppDateFormat.databaseDateTime(nullableValue(row, "selected_at")));
    }

    private String revisionToken(Object... components) {
        return Arrays.stream(components)
                .map(String::valueOf)
                .collect(Collectors.joining(":"));
    }

    private Object nullableValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = nullableValue(row, key);
        return value == null ? null : value.toString();
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = nullableValue(row, key);
        if (value == null) {
            return null;
        }

        return value instanceof Number number
                ? number.longValue()
                : Long.parseLong(value.toString());
    }

    private Integer nullableInteger(Map<String, Object> row, String key) {
        Object value = nullableValue(row, key);
        if (value == null) {
            return null;
        }

        return value instanceof Number number
                ? number.intValue()
                : Integer.parseInt(value.toString());
    }
}
