package com.gayadi.server.travel;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.KeyHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TripService {

    private final JdbcClient jdbc;
    private final UserService users;
    private final KeyHelper keyHelper;

    public TripService(JdbcClient jdbc, UserService users, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.users = users;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public Map<String, Object> create(CreateTrip command) {
        users.requireExists(command.ownerId());
        validateDeparture(command.departureMode(), command.meetingAt(), command.meetingPlaceId());
        long tripId = keyHelper.insert("""
                INSERT INTO trips (owner_id, title, start_date, end_date, departure_mode,
                                   meeting_at, meeting_place_id, region_id, trip_preferences, status, max_members)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PLANNING', ?)
                """,
                command.ownerId(), command.title(), command.startDate(), command.endDate(),
                command.departureMode().name(), command.meetingAt(), command.meetingPlaceId(),
                command.regionId(), command.tripPreferences(), command.maxMembers());
        addMemberInternal(tripId, command.ownerId(), "OWNER",
                command.ownerDeparturePlaceId(), command.ownerReturnPlaceId());
        return get(tripId);
    }

    @Transactional
    public Map<String, Object> addMember(long tripId, AddMember command) {
        requireTrip(tripId);
        users.requireExists(command.userId());
        addMemberInternal(tripId, command.userId(), "MEMBER",
                command.departurePlaceId(), command.returnPlaceId());
        return memberByUser(tripId, command.userId());
    }

    public Map<String, Object> get(long tripId) {
        Map<String, Object> row = jdbc.sql("""
                SELECT id, owner_id, title, description, start_date, end_date, departure_mode,
                       meeting_at, meeting_place_id, region_id, trip_preferences, status, max_members,
                       started_at, ended_at, created_at, updated_at
                FROM trips WHERE id = ? AND deleted_at IS NULL
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
        Map<String, Object> trip = new LinkedHashMap<>(row);
        trip.put("members", members(tripId));
        return trip;
    }

    public List<Map<String, Object>> members(long tripId) {
        requireTrip(tripId);
        return jdbc.sql("""
                SELECT tp.id, tp.user_id, u.nickname, tp.role, tp.status,
                       tp.departure_place_id, tp.return_place_id, tp.route_preferences
                FROM trip_participants tp
                JOIN users u ON u.id = tp.user_id
                WHERE tp.trip_id = ?
                ORDER BY tp.joined_at
                """)
                .param(tripId)
                .query().listOfRows();
    }

    @Transactional
    public Map<String, Object> start(long tripId) {
        int updated = jdbc.sql("""
                UPDATE trips SET status = 'IN_PROGRESS', started_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'PLANNING' AND deleted_at IS NULL
                """)
                .param(tripId)
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "PLANNING 상태에서만 시작할 수 있습니다.");
        }
        return get(tripId);
    }

    @Transactional
    public Map<String, Object> complete(long tripId) {
        int updated = jdbc.sql("""
                UPDATE trips SET status = 'COMPLETED', ended_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'IN_PROGRESS' AND deleted_at IS NULL
                """)
                .param(tripId)
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "진행 중인 여행만 완료할 수 있습니다.");
        }
        return get(tripId);
    }

    public Map<String, Object> requireTrip(long tripId) {
        return jdbc.sql("SELECT * FROM trips WHERE id = ? AND deleted_at IS NULL")
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    public void requireMember(long tripId, long userId) {
        long count = jdbc.sql("SELECT COUNT(*) FROM trip_participants WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new ApiException(HttpStatus.FORBIDDEN, "여행 멤버만 수행할 수 있습니다.");
        }
    }

    private void validateDeparture(DepartureMode mode, LocalDateTime meetingAt, Long meetingPlaceId) {
        if (mode == DepartureMode.TOGETHER && (meetingAt == null || meetingPlaceId == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "모여서 출발하는 여행은 집결 시각과 장소가 필요합니다.");
        }
    }

    private void addMemberInternal(long tripId, long userId, String role,
                                   Long departurePlaceId, Long returnPlaceId) {
        try {
            jdbc.sql("""
                    INSERT INTO trip_participants (trip_id, user_id, role, departure_place_id, return_place_id, status)
                    VALUES (?, ?, ?, ?, ?, 'JOINED')
                    """)
                    .params(tripId, userId, role, departurePlaceId, returnPlaceId)
                    .update();
        } catch (DuplicateKeyException e) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 참여 중인 멤버입니다.");
        }
    }

    private Map<String, Object> memberByUser(long tripId, long userId) {
        return jdbc.sql("SELECT * FROM trip_participants WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "참여자를 찾을 수 없습니다."));
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

    public record AddMember(
            long userId,
            Long departurePlaceId,
            Long returnPlaceId
    ) {
    }
}
