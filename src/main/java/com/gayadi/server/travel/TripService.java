package com.gayadi.server.travel;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.survey.SurveyService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class TripService {

    private static final int DEFAULT_MAX_MEMBERS = 20;
    private static final int MAX_LIST_SIZE = 100;
    private static final char[] INVITE_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int INVITE_CODE_ATTEMPTS = 20;

    private final JdbcClient jdbc;
    private final UserService users;
    private final KeyHelper keyHelper;
    private final SecureRandom random = new SecureRandom();

    public TripService(JdbcClient jdbc, UserService users, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.users = users;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public Map<String, Object> create(CreateTrip command) {
        users.lockActive(command.ownerId());
        validateTitle(command.title());
        validateDates(command.startDate(), command.endDate());
        validateDeparture(command.departureMode(), command.meetingAt(), command.meetingPlaceId());
        validateMaxMembers(command.maxMembers());
        long tripId = insertTrip(command);
        requireReachablePlace(tripId, command.ownerId(), command.meetingPlaceId());
        validateMemberPlaces(tripId, command.ownerId(),
                command.ownerDeparturePlaceId(), command.ownerReturnPlaceId());
        addMemberInternal(tripId, command.ownerId(), "OWNER",
                command.ownerDeparturePlaceId(), command.ownerReturnPlaceId());
        return get(tripId);
    }

    @Transactional
    public Map<String, Object> createForUser(
            long ownerId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            List<String> cities) {
        users.lockActive(ownerId);
        validateDates(startDate, endDate);
        validateTitle(title);
        List<String> normalizedCities = normalizeCities(cities);
        long regionId = resolveRegion(normalizedCities.getFirst());
        long tripId = insertTrip(new CreateTrip(
                ownerId, title.trim(), startDate, endDate, DepartureMode.SEPARATE,
                null, null, regionId, null, DEFAULT_MAX_MEMBERS, null, null));
        addMemberInternal(tripId, ownerId, "OWNER", null, null);
        replaceCities(tripId, normalizedCities);
        return view(tripId);
    }

    @Transactional
    public Map<String, Object> addMember(long tripId, AddMember command) {
        users.lockActive(command.userId());
        lockTrip(tripId);
        ensureMemberCanJoin(tripId);
        validateMemberPlaces(tripId, command.userId(), command.departurePlaceId(), command.returnPlaceId());
        addMemberInternal(tripId, command.userId(), "MEMBER",
                command.departurePlaceId(), command.returnPlaceId());
        return memberByUser(tripId, command.userId());
    }

    @Transactional
    public Map<String, Object> addMemberAsOwner(long actorId, long tripId, AddMember command) {
        lockUsersInOrder(actorId, command.userId());
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        ensureMemberCanJoin(tripId);
        validateMemberPlaces(tripId, command.userId(), command.departurePlaceId(), command.returnPlaceId());
        addMemberInternal(tripId, command.userId(), "MEMBER",
                command.departurePlaceId(), command.returnPlaceId());
        return memberByUser(tripId, command.userId());
    }

    @Transactional
    public void removeMember(long actorId, long tripId, long userId) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        if (actorId == userId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "여행 소유자는 내보낼 수 없습니다.");
        }
        int updated = jdbc.sql("""
                UPDATE trip_participants
                SET status = 'REMOVED', left_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND user_id = ? AND role = 'MEMBER' AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "참여자를 찾을 수 없습니다.");
        }
        jdbc.sql("DELETE FROM trip_date_availability_submissions WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId)
                .update();
        jdbc.sql("""
                UPDATE travel_routes
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE member_id IN (
                    SELECT id FROM trip_participants
                    WHERE trip_id = ? AND user_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .params(tripId, userId)
                .update();
    }

    public Map<String, Object> get(long tripId) {
        Map<String, Object> row = tripRow(tripId);
        Map<String, Object> trip = new LinkedHashMap<>(row);
        trip.put("members", members(tripId));
        return trip;
    }

    public Map<String, Object> view(long tripId) {
        Map<String, Object> row = tripRow(tripId);
        return toView(row, cities(tripId), members(tripId));
    }

    public List<Map<String, Object>> listForUser(long userId, String status, int requestedLimit, int offset) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int safeOffset = Math.max(0, offset);
        List<Map<String, Object>> rows;
        if (status == null || status.isBlank()) {
            rows = jdbc.sql("""
                    SELECT t.id, t.owner_id, t.title, t.start_date, t.end_date, t.departure_mode,
                           t.region_id, t.status, t.max_members, t.started_at, t.ended_at,
                           t.created_at, t.updated_at, t.version, t.invite_code
                    FROM trips t
                    JOIN trip_participants tp ON tp.trip_id = t.id
                    WHERE tp.user_id = ? AND tp.status = 'JOINED'
                      AND t.deleted_at IS NULL AND t.status <> 'CANCELED'
                    ORDER BY t.start_date DESC, t.id DESC
                    LIMIT ? OFFSET ?
                    """)
                    .params(userId, limit, safeOffset)
                    .query().listOfRows();
        } else {
            String normalizedStatus = normalizeStatus(status).name();
            rows = jdbc.sql("""
                    SELECT t.id, t.owner_id, t.title, t.start_date, t.end_date, t.departure_mode,
                           t.region_id, t.status, t.max_members, t.started_at, t.ended_at,
                           t.created_at, t.updated_at, t.version, t.invite_code
                    FROM trips t
                    JOIN trip_participants tp ON tp.trip_id = t.id
                    WHERE tp.user_id = ? AND tp.status = 'JOINED'
                      AND t.status = ? AND t.deleted_at IS NULL AND t.status <> 'CANCELED'
                    ORDER BY t.start_date DESC, t.id DESC
                    LIMIT ? OFFSET ?
                    """)
                    .params(userId, normalizedStatus, limit, safeOffset)
                    .query().listOfRows();
        }
        if (rows.isEmpty()) return List.of();

        List<Long> tripIds = rows.stream().map(row -> RowSupport.longValue(row, "id")).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(tripIds.size(), "?"));
        List<Map<String, Object>> cityRows = jdbc.sql("""
                SELECT trip_id, city_name, sequence_no FROM trip_cities
                WHERE trip_id IN (%s) ORDER BY trip_id, sequence_no
                """.formatted(placeholders))
                .params(tripIds.toArray())
                .query().listOfRows();
        List<Map<String, Object>> memberRows = jdbc.sql("""
                SELECT trip_id, user_id FROM trip_participants
                WHERE trip_id IN (%s) AND status = 'JOINED' ORDER BY trip_id, joined_at
                """.formatted(placeholders))
                .params(tripIds.toArray())
                .query().listOfRows();
        Map<Long, List<String>> citiesByTrip = new LinkedHashMap<>();
        cityRows.forEach(row -> citiesByTrip
                .computeIfAbsent(RowSupport.longValue(row, "trip_id"), ignored -> new ArrayList<>())
                .add(RowSupport.strValue(row, "city_name")));
        Map<Long, List<Map<String, Object>>> membersByTrip = new LinkedHashMap<>();
        memberRows.forEach(row -> membersByTrip
                .computeIfAbsent(RowSupport.longValue(row, "trip_id"), ignored -> new ArrayList<>())
                .add(Map.of("userId", RowSupport.longValue(row, "user_id"))));
        return rows.stream().map(row -> {
            long id = RowSupport.longValue(row, "id");
            return toView(row, citiesByTrip.getOrDefault(id, List.of()),
                    membersByTrip.getOrDefault(id, List.of()));
        }).toList();
    }

    public List<Map<String, Object>> members(long tripId) {
        requireTrip(tripId);
        return jdbc.sql("""
                SELECT tp.id, tp.user_id, u.nickname,
                       CASE WHEN a.result_code IS NULL THEN NULL
                            ELSE CONCAT('character_', LOWER(a.result_code)) END AS character_key,
                       tp.role, tp.status, tp.departure_place_id, tp.return_place_id,
                       tp.route_preferences, tp.joined_at
                FROM trip_participants tp
                JOIN users u ON u.id = tp.user_id
                LEFT JOIN survey_attempts a ON a.id = (
                    SELECT sa.id FROM survey_attempts sa
                    WHERE sa.user_id = tp.user_id AND sa.survey_id = ? AND sa.status = 'COMPLETED'
                    ORDER BY sa.completed_at DESC, sa.id DESC LIMIT 1
                )
                WHERE tp.trip_id = ? AND tp.status = 'JOINED'
                ORDER BY CASE tp.role WHEN 'OWNER' THEN 0 ELSE 1 END, tp.joined_at
                """)
                .params(SurveyService.PERSONALITY_SURVEY_ID, tripId)
                .query().listOfRows().stream()
                .map(this::toParticipant)
                .toList();
    }

    @Transactional
    public Map<String, Object> update(
            long actorId,
            long tripId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            List<String> cities,
            Integer expectedVersion) {
        Map<String, Object> trip = lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        validateDates(startDate, endDate);
        validateTitle(title);
        requirePlansWithinRange(tripId, startDate, endDate);
        List<String> normalizedCities = normalizeCities(cities);
        long regionId = resolveRegion(normalizedCities.getFirst());
        int updated;
        if (expectedVersion == null) {
            updated = jdbc.sql("""
                    UPDATE trips SET title = ?, start_date = ?, end_date = ?, region_id = ?,
                                     version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND deleted_at IS NULL
                    """)
                    .params(title.trim(), startDate, endDate, regionId, tripId)
                    .update();
        } else {
            updated = jdbc.sql("""
                    UPDATE trips SET title = ?, start_date = ?, end_date = ?, region_id = ?,
                                     version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND version = ? AND deleted_at IS NULL
                    """)
                    .params(title.trim(), startDate, endDate, regionId, tripId, expectedVersion)
                    .update();
        }
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "다른 사용자가 여행 정보를 먼저 바꿨습니다. 다시 불러와 주세요.");
        }
        replaceCities(tripId, normalizedCities);
        recalculatePlanDays(tripId, startDate);
        return view(tripId);
    }

    @Transactional
    public Map<String, Object> finalizeDates(
            long actorId, long tripId, LocalDate startDate, LocalDate endDate) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        validateDates(startDate, endDate);
        requirePlansWithinRange(tripId, startDate, endDate);
        jdbc.sql("""
                UPDATE trips
                SET start_date = ?, end_date = ?, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .params(startDate, endDate, tripId)
                .update();
        recalculatePlanDays(tripId, startDate);
        return view(tripId);
    }

    private void requirePlansWithinRange(long tripId, LocalDate startDate, LocalDate endDate) {
        long schedulesOutsideRange = jdbc.sql("""
                SELECT COUNT(*) FROM travel_plans
                WHERE trip_id = ? AND (plan_date < ? OR plan_date > ?)
                """)
                .params(tripId, startDate, endDate)
                .query(Long.class)
                .single();
        if (schedulesOutsideRange > 0) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "바꿀 여행 기간 밖에 일정이 있습니다. 일정을 먼저 정리해 주세요.");
        }
    }

    @Transactional
    public Map<String, Object> changeStatus(long actorId, long tripId, TripStatus target) {
        Map<String, Object> trip = lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        TripStatus current = TripStatus.valueOf(RowSupport.strValue(trip, "status"));
        if (!allowedTransition(current, target)) {
            throw new ApiException(HttpStatus.CONFLICT,
                    statusLabel(current) + " 상태에서는 " + statusLabel(target) + " 상태로 바꿀 수 없습니다.");
        }
        String timestamps = switch (target) {
            case IN_PROGRESS -> ", started_at = CURRENT_TIMESTAMP";
            case COMPLETED -> ", ended_at = CURRENT_TIMESTAMP";
            default -> "";
        };
        jdbc.sql("UPDATE trips SET status = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP"
                        + timestamps + " WHERE id = ?")
                .params(target.name(), tripId)
                .update();
        return view(tripId);
    }

    @Transactional
    public Map<String, Object> changeStatus(long actorId, long tripId, String target) {
        return changeStatus(actorId, tripId, normalizeStatus(target));
    }

    @Transactional
    public void delete(long actorId, long tripId) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        int updated = jdbc.sql("""
                UPDATE trips SET status = 'CANCELED', deleted_at = CURRENT_TIMESTAMP,
                                 version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted_at IS NULL
                """)
                .param(tripId)
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다.");
        }
    }

    @Transactional
    public Map<String, Object> start(long tripId) {
        return transitionWithoutActor(tripId, TripStatus.PLANNING, TripStatus.IN_PROGRESS);
    }

    @Transactional
    public Map<String, Object> complete(long tripId) {
        return transitionWithoutActor(tripId, TripStatus.IN_PROGRESS, TripStatus.COMPLETED);
    }

    public Map<String, Object> requireTrip(long tripId) {
        return tripRow(tripId);
    }

    public void requireOwner(long tripId, long userId) {
        requireTrip(tripId);
        requireOwnerRow(tripId, userId);
    }

    public void requireMember(long tripId, long userId) {
        long count = jdbc.sql("""
                SELECT COUNT(*)
                FROM trip_participants tp
                JOIN trips t ON t.id = tp.trip_id
                WHERE tp.trip_id = ? AND tp.user_id = ? AND tp.status = 'JOINED'
                  AND t.deleted_at IS NULL
                """)
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "여행 참여자만 처리할 수 있습니다.");
        }
    }

    private Map<String, Object> transitionWithoutActor(long tripId, TripStatus current, TripStatus target) {
        String timeColumn = target == TripStatus.IN_PROGRESS ? "started_at" : "ended_at";
        int updated = jdbc.sql("UPDATE trips SET status = ?, " + timeColumn
                        + " = CURRENT_TIMESTAMP, version = version + 1, updated_at = CURRENT_TIMESTAMP"
                        + " WHERE id = ? AND status = ? AND deleted_at IS NULL")
                .params(target.name(), tripId, current.name())
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 상태를 바꿀 수 없습니다.");
        }
        return get(tripId);
    }

    private long insertTrip(CreateTrip command) {
        for (int attempt = 0; attempt < INVITE_CODE_ATTEMPTS; attempt++) {
            String inviteCode = availableTripCode();
            var inserted = keyHelper.insertOrEmptyOnUniqueViolation("""
                        INSERT INTO trips (owner_id, title, start_date, end_date, departure_mode,
                                           meeting_at, meeting_place_id, region_id, trip_preferences,
                                           status, max_members, invite_code)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLANNING', ?, ?)
                        """,
                    command.ownerId(), command.title(), command.startDate(), command.endDate(),
                    command.departureMode().name(), command.meetingAt(), command.meetingPlaceId(),
                    command.regionId(), command.tripPreferences(),
                    command.maxMembers() == null ? DEFAULT_MAX_MEMBERS : command.maxMembers(),
                    inviteCode);
            if (inserted.isPresent()) {
                return inserted.getAsLong();
            }
            // 드문 코드 충돌이면 저장점으로 복구된 같은 트랜잭션에서 새 코드를 만든다.
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "여행 초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private Map<String, Object> tripRow(long tripId) {
        return jdbc.sql("""
                SELECT id, owner_id, title, description, start_date, end_date, departure_mode,
                       meeting_at, meeting_place_id, region_id, trip_preferences, status, max_members,
                       started_at, ended_at, created_at, updated_at, version, invite_code
                FROM trips WHERE id = ? AND deleted_at IS NULL AND status <> 'CANCELED'
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private Map<String, Object> lockTrip(long tripId) {
        return jdbc.sql("SELECT * FROM trips WHERE id = ? AND deleted_at IS NULL AND status <> 'CANCELED' FOR UPDATE")
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private void requireOwnerRow(long tripId, long userId) {
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND role = 'OWNER' AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "여행 소유자만 처리할 수 있습니다.");
        }
    }

    private void ensureMemberCanJoin(long tripId) {
        Map<String, Object> trip = tripRow(tripId);
        if (!"PLANNING".equals(RowSupport.strValue(trip, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "준비 중인 여행에만 참여할 수 있습니다.");
        }
        Object maxMembersValue = valueOrNull(trip, "max_members");
        int maxMembers = maxMembersValue == null
                ? DEFAULT_MAX_MEMBERS
                : ((Number) maxMembersValue).intValue();
        Long current = jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants WHERE trip_id = ? AND status = 'JOINED'
                """)
                .param(tripId)
                .query(Long.class)
                .single();
        if (current >= maxMembers) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 참여 인원이 가득 찼습니다.");
        }
    }

    private void addMemberInternal(long tripId, long userId, String role,
                                   Long departurePlaceId, Long returnPlaceId) {
        int restored = jdbc.sql("""
                UPDATE trip_participants
                SET role = ?, status = 'JOINED', departure_place_id = ?, return_place_id = ?,
                    left_at = NULL, joined_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND user_id = ? AND status IN ('LEFT', 'REMOVED')
                """)
                .params(role, departurePlaceId, returnPlaceId, tripId, userId)
                .update();
        if (restored == 1) {
            jdbc.sql("""
                    UPDATE travel_routes
                    SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE member_id IN (
                        SELECT id FROM trip_participants
                        WHERE trip_id = ? AND user_id = ?)
                      AND status IN ('RECOMMENDED', 'SELECTED')
                    """)
                    .params(tripId, userId)
                    .update();
            return;
        }
        try {
            jdbc.sql("""
                    INSERT INTO trip_participants
                        (trip_id, user_id, role, departure_place_id, return_place_id, status)
                    VALUES (?, ?, ?, ?, ?, 'JOINED')
                    """)
                    .params(tripId, userId, role, departurePlaceId, returnPlaceId)
                    .update();
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 참여한 여행입니다.");
        }
    }

    private Map<String, Object> memberByUser(long tripId, long userId) {
        return jdbc.sql("""
                SELECT tp.id, tp.trip_id, tp.user_id, u.nickname,
                       CAST(NULL AS VARCHAR) AS character_key,
                       tp.role, tp.status, tp.departure_place_id,
                       tp.return_place_id, tp.route_preferences, tp.joined_at
                FROM trip_participants tp
                JOIN users u ON u.id = tp.user_id
                WHERE tp.trip_id = ? AND tp.user_id = ?
                """)
                .params(tripId, userId)
                .query().listOfRows().stream()
                .findFirst()
                .map(this::toParticipant)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "참여자를 찾을 수 없습니다."));
    }

    private List<String> cities(long tripId) {
        return jdbc.sql("""
                SELECT city_name FROM trip_cities WHERE trip_id = ? ORDER BY sequence_no
                """)
                .param(tripId)
                .query(String.class)
                .list();
    }

    private Map<String, Object> toView(
            Map<String, Object> row,
            List<String> cities,
            List<Map<String, Object>> members) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", RowSupport.longValue(row, "id"));
        result.put("name", RowSupport.strValue(row, "title"));
        result.put("startDate", AppDateFormat.date(localDate(RowSupport.value(row, "start_date"))));
        result.put("endDate", AppDateFormat.date(localDate(RowSupport.value(row, "end_date"))));
        result.put("cities", cities);
        String status = RowSupport.strValue(row, "status");
        result.put("status", "IN_PROGRESS".equals(status) ? "ONGOING" : status);
        result.put("ownerId", RowSupport.longValue(row, "owner_id"));
        result.put("participantIds", members.stream()
                .map(member -> {
                    Object value = member.get("user_id");
                    if (value == null) value = member.get("userId");
                    return ((Number) value).longValue();
                })
                .toList());
        Object storedInviteCode = valueOrNull(row, "invite_code");
        result.put("inviteCode", storedInviteCode == null ? "" : storedInviteCode.toString());
        result.put("version", RowSupport.intValue(row, "version"));
        result.put("createdAt", RowSupport.value(row, "created_at"));
        result.put("updatedAt", RowSupport.value(row, "updated_at"));
        return result;
    }

    private Map<String, Object> toParticipant(Map<String, Object> row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", RowSupport.longValue(row, "user_id"));
        value.put("userId", RowSupport.longValue(row, "user_id"));
        value.put("participantId", RowSupport.longValue(row, "id"));
        value.put("nickname", RowSupport.strValue(row, "nickname"));
        value.put("characterKey", valueOrNull(row, "character_key"));
        value.put("role", RowSupport.strValue(row, "role"));
        value.put("status", RowSupport.strValue(row, "status"));
        value.put("departurePlaceId", valueOrNull(row, "departure_place_id"));
        value.put("returnPlaceId", valueOrNull(row, "return_place_id"));
        return value;
    }

    private Object valueOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private List<String> normalizeCities(List<String> cities) {
        if (cities == null) throw new ApiException(HttpStatus.BAD_REQUEST, "여행 도시를 하나 이상 골라 주세요.");
        List<String> normalized = cities.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(10)
                .toList();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "여행 도시를 하나 이상 골라 주세요.");
        }
        return normalized;
    }

    private long resolveRegion(String name) {
        Long regionId = jdbc.sql("SELECT region_id FROM regions WHERE name = ?")
                .param(name)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (regionId != null) return regionId;

        // PostgreSQL은 유일 제약 오류가 난 트랜잭션에서 재조회할 수 없으므로
        // 기준 지역 행을 잠가 새로운 지역 등록을 짧게 직렬화한다.
        jdbc.sql("""
                SELECT region_id FROM regions
                WHERE region_id = (SELECT MIN(region_id) FROM regions)
                FOR UPDATE
                """)
                .query(Long.class)
                .optional();
        regionId = jdbc.sql("SELECT region_id FROM regions WHERE name = ?")
                .param(name)
                .query(Long.class)
                .optional()
                .orElse(null);
        if (regionId != null) return regionId;

        jdbc.sql("INSERT INTO regions (name) VALUES (?)").param(name).update();
        return jdbc.sql("SELECT region_id FROM regions WHERE name = ?")
                .param(name)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "여행 지역을 등록하지 못했습니다."));
    }

    private void replaceCities(long tripId, List<String> cities) {
        jdbc.sql("DELETE FROM trip_cities WHERE trip_id = ?").param(tripId).update();
        for (int index = 0; index < cities.size(); index++) {
            jdbc.sql("INSERT INTO trip_cities (trip_id, city_name, sequence_no) VALUES (?, ?, ?)")
                    .params(tripId, cities.get(index), index + 1)
                    .update();
        }
    }

    private void recalculatePlanDays(long tripId, LocalDate startDate) {
        List<Map<String, Object>> plans = jdbc.sql("""
                SELECT id, plan_date FROM travel_plans
                WHERE trip_id = ? ORDER BY plan_date, id
                FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows();
        if (plans.isEmpty()) return;

        // 같은 여행의 일차 유일 제약과 부딪치지 않도록 기존 값을 먼저 임시 범위로 옮긴다.
        jdbc.sql("UPDATE travel_plans SET day_number = day_number + 10000 WHERE trip_id = ?")
                .param(tripId)
                .update();
        for (Map<String, Object> plan : plans) {
            LocalDate planDate = localDate(RowSupport.value(plan, "plan_date"));
            int dayNumber = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(startDate, planDate)) + 1;
            jdbc.sql("""
                    UPDATE travel_plans
                    SET day_number = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND trip_id = ?
                    """)
                    .params(dayNumber, RowSupport.longValue(plan, "id"), tripId)
                    .update();
        }
    }

    private String availableTripCode() {
        for (int attempt = 0; attempt < INVITE_CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder("G");
            for (int index = 1; index < 6; index++) {
                code.append(INVITE_CODE_CHARACTERS[random.nextInt(INVITE_CODE_CHARACTERS.length)]);
            }
            long count = jdbc.sql("""
                    SELECT
                      (SELECT COUNT(*) FROM trips WHERE invite_code = ?) +
                      (SELECT COUNT(*) FROM travel_invitations WHERE invite_code = ?)
                    """)
                    .params(code.toString(), code.toString())
                    .query(Long.class)
                    .single();
            if (count == 0) return code.toString();
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "여행 초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private LocalDate localDate(Object value) {
        return AppDateFormat.databaseDate(value);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "여행 종료일은 시작일과 같거나 뒤여야 합니다.");
        }
        if (startDate.plusDays(30).isBefore(endDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "한 여행은 31일까지 만들 수 있습니다.");
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "여행 이름은 1자에서 100자 사이여야 합니다.");
        }
    }

    private void validateMemberPlaces(
            long tripId, long userId, Long departurePlaceId, Long returnPlaceId) {
        requireReachablePlace(tripId, userId, departurePlaceId);
        requireReachablePlace(tripId, userId, returnPlaceId);
    }

    private void requireReachablePlace(long tripId, long userId, Long placeId) {
        if (placeId == null) return;
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM places
                WHERE id = ? AND status = 'ACTIVE'
                  AND (visibility = 'PUBLIC' OR owner_user_id = ? OR trip_id = ?)
                """)
                .params(placeId, userId, tripId)
                .query(Long.class)
                .single();
        if (count == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "출발지나 귀가 장소를 확인할 수 없습니다.");
        }
    }

    private void validateDeparture(DepartureMode mode, LocalDateTime meetingAt, Long meetingPlaceId) {
        if (mode == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "출발 방식을 골라 주세요.");
        }
        if (mode == DepartureMode.TOGETHER && (meetingAt == null || meetingPlaceId == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "함께 출발할 때는 모이는 시각과 장소가 필요합니다.");
        }
    }

    private void validateMaxMembers(Integer maxMembers) {
        if (maxMembers != null && (maxMembers < 1 || maxMembers > 100)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "참여 인원은 1명에서 100명 사이여야 합니다.");
        }
    }

    private void lockUsersInOrder(long firstUserId, long secondUserId) {
        users.lockActive(Math.min(firstUserId, secondUserId));
        if (firstUserId != secondUserId) {
            users.lockActive(Math.max(firstUserId, secondUserId));
        }
    }

    private TripStatus normalizeStatus(String status) {
        try {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if ("ONGOING".equals(normalized)) normalized = "IN_PROGRESS";
            if ("CANCELED".equals(normalized)) {
                throw new IllegalArgumentException("취소 상태는 삭제 API로 처리합니다.");
            }
            return TripStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "올바르지 않은 여행 상태입니다.");
        }
    }

    private boolean allowedTransition(TripStatus current, TripStatus target) {
        return (current == TripStatus.PLANNING && (target == TripStatus.IN_PROGRESS || target == TripStatus.CANCELED))
                || (current == TripStatus.IN_PROGRESS && (target == TripStatus.COMPLETED || target == TripStatus.CANCELED));
    }

    private String statusLabel(TripStatus status) {
        return switch (status) {
            case PLANNING -> "준비 중";
            case IN_PROGRESS -> "여행 중";
            case COMPLETED -> "완료";
            case CANCELED -> "취소";
        };
    }

    public record CreateTrip(
            long ownerId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            DepartureMode departureMode,
            LocalDateTime meetingAt,
            Long meetingPlaceId,
            long regionId,
            String tripPreferences,
            Integer maxMembers,
            Long ownerDeparturePlaceId,
            Long ownerReturnPlaceId
    ) {
    }

    public record AddMember(long userId, Long departurePlaceId, Long returnPlaceId) {
    }
}
