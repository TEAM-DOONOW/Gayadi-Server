package com.gayadi.server.travel;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.model.TripStatus;
import com.gayadi.server.travel.query.ParticipantQueryResult;
import com.gayadi.server.travel.query.TripQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** 여행과 참여자 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class TripRepository {

    private static final String TRIP_COLUMNS = """
            id, owner_id, title, description, start_date, end_date, departure_mode,
            meeting_at, meeting_place_id, region_id, trip_preferences, status, max_members,
            started_at, ended_at, created_at, updated_at, version, invite_code
            """;

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public TripRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 여행 조건에 맞는 여행 데이터를 DB에서 조회합니다. */
    public Optional<TripQueryResult> find(long tripId) {
        return jdbc.sql("SELECT " + TRIP_COLUMNS + " FROM trips "
                        + "WHERE id = ? AND deleted_at IS NULL AND status <> 'CANCELED'")
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapTrip);
    }

    /** 변경 충돌을 막기 위해 여행 DB 행을 잠급니다. */
    public Optional<TripQueryResult> lock(long tripId) {
        return jdbc.sql("SELECT " + TRIP_COLUMNS + " FROM trips "
                        + "WHERE id = ? AND deleted_at IS NULL AND status <> 'CANCELED' FOR UPDATE")
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapTrip);
    }

    /** 전체 사용자 정보를 DB에서 조회합니다. */
    public List<TripQueryResult> findAllForUser(
            long userId,
            TripStatus status,
            int limit,
            int offset) {
        String statusClause = status == null ? "" : " AND t.status = ?";
        var statement = jdbc.sql("""
                SELECT t.id, t.owner_id, t.title, t.description, t.start_date, t.end_date,
                       t.departure_mode, t.meeting_at, t.meeting_place_id, t.region_id,
                       t.trip_preferences, t.status, t.max_members, t.started_at, t.ended_at,
                       t.created_at, t.updated_at, t.version, t.invite_code
                FROM trips t JOIN trip_participants tp ON tp.trip_id = t.id
                WHERE tp.user_id = ? AND tp.status = 'JOINED'
                  AND t.deleted_at IS NULL AND t.status <> 'CANCELED'
                """ + statusClause + " ORDER BY t.start_date DESC, t.id DESC LIMIT ? OFFSET ?");
        if (status == null) {
            statement.params(
                    userId,
                    limit,
                    offset);
        } else {
            statement.params(
                    userId,
                    status.name(),
                    limit,
                    offset);
        }

        return statement
                .query()
                .listOfRows()
                .stream()
                .map(this::mapTrip)
                .toList();
    }

    /** 도시 정보를 DB에서 조회합니다. */
    public List<String> findCities(long tripId) {
        return jdbc.sql("SELECT city_name FROM trip_cities WHERE trip_id = ? ORDER BY sequence_no")
                .param(tripId)
                .query(String.class)
                .list();
    }

    /** 도시 여행 식별자 정보를 DB에서 조회합니다. */
    public Map<Long, List<String>> findCitiesByTripIds(List<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(tripIds.size(), "?"));
        Map<Long, List<String>> result = new java.util.LinkedHashMap<>();
        jdbc.sql("SELECT trip_id, city_name FROM trip_cities WHERE trip_id IN (" + placeholders
                        + ") ORDER BY trip_id, sequence_no")
                .params(tripIds.toArray())
                .query()
                .listOfRows()
                .forEach(row -> result
                        .computeIfAbsent(RowSupport.longValue(row, "trip_id"), ignored -> new java.util.ArrayList<>())
                        .add(RowSupport.strValue(row, "city_name")));
        return result;
    }

    /** 참여 중 사용자 식별자 목록 조건에 맞는 여행 데이터를 DB에서 조회합니다. */
    public List<Long> findJoinedUserIds(long tripId) {
        return jdbc.sql("""
                SELECT user_id FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED' ORDER BY joined_at
                """)
                .param(tripId)
                .query(Long.class)
                .list();
    }

    /** 참여 중 사용자 식별자 여행 식별자 정보를 DB에서 조회합니다. */
    public Map<Long, List<Long>> findJoinedUserIdsByTripIds(List<Long> tripIds) {
        if (tripIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", Collections.nCopies(tripIds.size(), "?"));
        Map<Long, List<Long>> result = new java.util.LinkedHashMap<>();
        jdbc.sql("SELECT trip_id, user_id FROM trip_participants WHERE trip_id IN (" + placeholders
                        + ") AND status = 'JOINED' ORDER BY trip_id, joined_at")
                .params(tripIds.toArray())
                .query()
                .listOfRows()
                .forEach(row -> result
                        .computeIfAbsent(RowSupport.longValue(row, "trip_id"), ignored -> new java.util.ArrayList<>())
                        .add(RowSupport.longValue(row, "user_id")));
        return result;
    }

    /** 참여자 목록 조건에 맞는 여행 데이터를 DB에서 조회합니다. */
    public List<ParticipantQueryResult> findParticipants(long tripId) {

        return jdbc.sql("""
                SELECT tp.id, tp.trip_id, tp.user_id, u.nickname,
                       CASE WHEN a.result_code IS NULL THEN NULL
                            ELSE CONCAT('character_', LOWER(a.result_code)) END AS character_key,
                       tp.role, tp.status, tp.departure_place_id, tp.return_place_id
                FROM trip_participants tp JOIN users u ON u.id = tp.user_id
                LEFT JOIN survey_attempts a ON a.id = (
                    SELECT sa.id FROM survey_attempts sa
                    WHERE sa.user_id = tp.user_id AND sa.survey_id = ? AND sa.status = 'COMPLETED'
                    ORDER BY sa.completed_at DESC, sa.id DESC LIMIT 1)
                WHERE tp.trip_id = ? AND tp.status = 'JOINED'
                ORDER BY CASE tp.role WHEN 'OWNER' THEN 0 ELSE 1 END, tp.joined_at
                """).params(SurveyService.PERSONALITY_SURVEY_ID, tripId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapParticipant)
                .toList();

    }

    /** 참여자 조건에 맞는 여행 데이터를 DB에서 조회합니다. */
    public Optional<ParticipantQueryResult> findParticipant(long tripId, long userId) {
        return jdbc.sql("""
                SELECT tp.id, tp.trip_id, tp.user_id, u.nickname,
                       CAST(NULL AS VARCHAR) AS character_key, tp.role, tp.status,
                       tp.departure_place_id, tp.return_place_id
                FROM trip_participants tp JOIN users u ON u.id = tp.user_id
                WHERE tp.trip_id = ? AND tp.user_id = ?
                """)
                .params(tripId, userId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapParticipant);
    }

    /** 여행 여부나 개수를 DB에서 확인합니다. */
    public boolean isOwner(long tripId, long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND role = 'OWNER' AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElse(0L) > 0;
    }

    /** 참여자 여부나 개수를 DB에서 확인합니다. */
    public boolean isMember(long tripId, long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants tp JOIN trips t ON t.id = tp.trip_id
                WHERE tp.trip_id = ? AND tp.user_id = ? AND tp.status = 'JOINED'
                  AND t.deleted_at IS NULL
                """)
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElse(0L) > 0;
    }

    /** DB에서 참여 중 참여자 목록 개수를 집계합니다. */
    public long countJoinedMembers(long tripId) {
        return jdbc.sql("SELECT COUNT(*) FROM trip_participants WHERE trip_id = ? AND status = 'JOINED'")
                .param(tripId)
                .query(Long.class)
                .single();
    }

    /** 여행 여행 데이터를 DB에 저장합니다. */
    public java.util.OptionalLong insert(TripService.CreateTrip command, int defaultMaxMembers, String inviteCode) {
        return keyHelper.insertOrEmptyOnUniqueViolation("""
                INSERT INTO trips (owner_id, title, start_date, end_date, departure_mode,
                    meeting_at, meeting_place_id, region_id, trip_preferences,
                    status, max_members, invite_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLANNING', ?, ?)
                """, command.ownerId(), command.title(), command.startDate(), command.endDate(),
                command.departureMode().name(), command.meetingAt(), command.meetingPlaceId(),
                command.regionId(), command.tripPreferences(),
                command.maxMembers() == null ? defaultMaxMembers : command.maxMembers(), inviteCode);
    }

    /** 코드에 대한 여행 기능을 처리합니다. */
    public boolean codeExists(String code) {
        return jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM trips WHERE invite_code = ?)
                     + (SELECT COUNT(*) FROM travel_invitations WHERE invite_code = ?)
                """)
                .params(code, code)
                .query(Long.class)
                .single() > 0;
    }

    /** 참여자 상태나 값을 DB에 반영합니다. */
    public boolean restoreParticipant(long tripId, long userId, String role,
            Long departurePlaceId, Long returnPlaceId) {
        return jdbc.sql("""
                UPDATE trip_participants SET role = ?, status = 'JOINED', departure_place_id = ?,
                    return_place_id = ?, left_at = NULL, joined_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND user_id = ? AND status IN ('LEFT', 'REMOVED')
                """)
                .params(role, departurePlaceId, returnPlaceId, tripId, userId)
                .update() == 1;
    }

    /** 참여자 여행 데이터를 DB에 저장합니다. */
    public void insertParticipant(long tripId, long userId, String role,
            Long departurePlaceId, Long returnPlaceId) {
        jdbc.sql("""
                INSERT INTO trip_participants
                    (trip_id, user_id, role, departure_place_id, return_place_id, status)
                VALUES (?, ?, ?, ?, ?, 'JOINED')
                """)
                .params(tripId, userId, role, departurePlaceId, returnPlaceId)
                .update();
    }

    /** 참여자 경로 목록 여행 상태를 DB에서 만료 또는 해제합니다. */
    public void expireParticipantRoutes(long tripId, long userId) {
        jdbc.sql("""
                UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE member_id IN (SELECT id FROM trip_participants WHERE trip_id = ? AND user_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .params(tripId, userId)
                .update();
    }

    /** 참여자 여행 데이터를 DB에서 삭제합니다. */
    public boolean removeParticipant(long tripId, long userId) {
        int updated = jdbc.sql("""
                UPDATE trip_participants SET status = 'REMOVED', left_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND user_id = ? AND role = 'MEMBER' AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .update();
        if (updated == 0) {
            return false;
        }
        jdbc.sql("DELETE FROM trip_date_availability_submissions WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId)
                .update();
        expireParticipantRoutes(tripId, userId);
        return true;
    }

    /** 기간 밖 일정 관련 여행 업무를 처리합니다. */
    public boolean plansExistOutside(long tripId, LocalDate startDate, LocalDate endDate) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM travel_plans
                WHERE trip_id = ? AND (plan_date < ? OR plan_date > ?)
                """)
                .params(tripId, startDate, endDate)
                .query(Long.class)
                .single() > 0;
    }

    /** 여행 여행 상태를 DB에 반영합니다. */
    public boolean updateTrip(long tripId, String title, LocalDate startDate, LocalDate endDate,
            long regionId, Integer expectedVersion) {
        if (expectedVersion == null) {
            return jdbc.sql("""
                    UPDATE trips SET title = ?, start_date = ?, end_date = ?, region_id = ?,
                        version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND deleted_at IS NULL
                    """)
                    .params(title, startDate, endDate, regionId, tripId)
                    .update() == 1;
        }
        return jdbc.sql("""
                UPDATE trips SET title = ?, start_date = ?, end_date = ?, region_id = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ? AND deleted_at IS NULL
                """)
                .params(title, startDate, endDate, regionId, tripId, expectedVersion)
                .update() == 1;
    }

    /** 날짜 여행 상태를 DB에 반영합니다. */
    public void updateDates(long tripId, LocalDate startDate, LocalDate endDate) {
        jdbc.sql("""
                UPDATE trips SET start_date = ?, end_date = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """)
                .params(startDate, endDate, tripId)
                .update();
    }

    /** 상태 여행 상태를 DB에 반영합니다. */
    public void updateStatus(long tripId, TripStatus status) {
        String timestamps = switch (status) {
            case IN_PROGRESS -> ", started_at = CURRENT_TIMESTAMP";
            case COMPLETED -> ", ended_at = CURRENT_TIMESTAMP";
            default -> "";
        };
        jdbc.sql("UPDATE trips SET status = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP"
                + timestamps + " WHERE id = ?")
                .params(status.name(), tripId)
                .update();
    }

    /** 여행 상태나 값을 DB에 반영합니다. */
    public boolean transition(long tripId, TripStatus current, TripStatus target) {
        String timeColumn = target == TripStatus.IN_PROGRESS ? "started_at" : "ended_at";
        return jdbc.sql("UPDATE trips SET status = ?, " + timeColumn
                + " = CURRENT_TIMESTAMP, version = version + 1, updated_at = CURRENT_TIMESTAMP"
                + " WHERE id = ? AND status = ? AND deleted_at IS NULL")
                .params(target.name(), tripId, current.name())
                .update() == 1;
    }

    /** 여행 상태를 만료하거나 해제합니다. */
    public boolean cancel(long tripId) {
        return jdbc.sql("""
                UPDATE trips SET status = 'CANCELED', deleted_at = CURRENT_TIMESTAMP,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """)
                .param(tripId)
                .update() == 1;
    }

    /** 지역 식별자 조건에 맞는 여행 데이터를 DB에서 조회합니다. */
    public Optional<Long> findRegionId(String name) {
        return jdbc.sql("SELECT region_id FROM regions WHERE name = ?")
                .param(name)
                .query(Long.class)
                .optional();
    }

    /** 동시 변경을 막기 위해 지역 순번 DB 행을 잠급니다. */
    public void lockRegionSequence() {
        jdbc.sql("""
                SELECT region_id FROM regions
                WHERE region_id = (SELECT MIN(region_id) FROM regions) FOR UPDATE
                """)
                .query(Long.class)
                .optional();
    }

    /** 지역 여행 데이터를 DB에 저장합니다. */
    public void insertRegion(String name) {
        jdbc.sql("INSERT INTO regions (name) VALUES (?)")
        .param(name)
        .update();
    }

    /** 도시 정보를 새 값으로 교체합니다. */
    public void replaceCities(long tripId, List<String> cities) {
        jdbc.sql("DELETE FROM trip_cities WHERE trip_id = ?")
        .param(tripId)
        .update();
        for (int index = 0; index < cities.size(); index++) {
            jdbc.sql("INSERT INTO trip_cities (trip_id, city_name, sequence_no) VALUES (?, ?, ?)")
                    .params(tripId, cities.get(index), index + 1)
                    .update();
        }
    }

    /** 계획 일차 계획 상태나 값을 DB에 반영합니다. */
    public void recalculatePlanDays(long tripId, LocalDate startDate) {
        List<Map<String, Object>> plans = jdbc.sql("""
                SELECT id, plan_date FROM travel_plans
                WHERE trip_id = ? ORDER BY plan_date, id FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows();
        if (plans.isEmpty()) {
            return;
        }
        jdbc.sql("UPDATE travel_plans SET day_number = day_number + 10000 WHERE trip_id = ?")
                .param(tripId)
                .update();
        for (Map<String, Object> plan : plans) {
            LocalDate planDate = AppDateFormat.databaseDate(RowSupport.value(plan, "plan_date"));
            int dayNumber = Math.toIntExact(ChronoUnit.DAYS.between(startDate, planDate)) + 1;
            jdbc.sql("""
                    UPDATE travel_plans SET day_number = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND trip_id = ?
                    """)
                    .params(dayNumber, RowSupport.longValue(plan, "id"), tripId)
                    .update();
        }
    }

    /** 접근 가능 장소 여부나 개수를 DB에서 확인합니다. */
    public boolean isReachablePlace(long tripId, long userId, long placeId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM places WHERE id = ? AND status = 'ACTIVE'
                  AND (visibility = 'PUBLIC' OR owner_user_id = ? OR trip_id = ?)
                """)
                .params(placeId, userId, tripId)
                .query(Long.class)
                .single() > 0;
    }

    private TripQueryResult mapTrip(Map<String, Object> row) {
        return new TripQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "owner_id"),
                RowSupport.strValue(row, "title"),
                text(row, "description"),
                AppDateFormat.databaseDate(RowSupport.value(row, "start_date")),
                AppDateFormat.databaseDate(RowSupport.value(row, "end_date")),
                DepartureMode.valueOf(RowSupport.strValue(row, "departure_mode")),
                dateTime(row, "meeting_at"),
                longValue(row, "meeting_place_id"),
                RowSupport.longValue(row, "region_id"),
                text(row, "trip_preferences"),
                TripStatus.valueOf(RowSupport.strValue(row, "status")),
                intValue(row, "max_members"),
                dateTime(row, "started_at"),
                dateTime(row, "ended_at"),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "created_at")),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "updated_at")),
                RowSupport.intValue(row, "version"),
                text(row, "invite_code"));
    }

    private ParticipantQueryResult mapParticipant(Map<String, Object> row) {
        return new ParticipantQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "trip_id"),
                RowSupport.longValue(row, "user_id"),
                RowSupport.strValue(row, "nickname"),
                text(row, "character_key"),
                RowSupport.strValue(row, "role"),
                RowSupport.strValue(row, "status"),
                longValue(row, "departure_place_id"),
                longValue(row, "return_place_id"));
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null
                : value instanceof Number number ? number.longValue() : Long.valueOf(value.toString());
    }

    private Integer intValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null
                : value instanceof Number number ? number.intValue() : Integer.valueOf(value.toString());
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }
}
