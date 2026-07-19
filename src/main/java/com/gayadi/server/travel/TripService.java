package com.gayadi.server.travel;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.Location;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class TripService {
    private final JdbcClient jdbc;
    private final UserService users;
    private final JsonSupport json;

    public TripService(JdbcClient jdbc, UserService users, JsonSupport json) {
        this.jdbc = jdbc;
        this.users = users;
        this.json = json;
    }

    @Transactional
    public Map<String, Object> create(CreateTrip command) {
        users.requireExists(command.ownerId());
        validateDeparture(command.departureMode(), command.meetingAt(), command.meetingLocation());
        String tripId = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO trips(id, owner_id, title, departure_mode, departure_at, meeting_at, meeting_location)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """).params(tripId, command.ownerId(), command.title(), command.departureMode().name(),
                        command.departureAt(), command.meetingAt(), nullableJson(command.meetingLocation())).update();
        addMemberInternal(tripId, command.ownerId(), "OWNER", command.ownerDeparture(), command.ownerReturn());
        return get(tripId);
    }

    @Transactional
    public Map<String, Object> addMember(String tripId, AddMember command) {
        requireTrip(tripId);
        users.requireExists(command.userId());
        addMemberInternal(tripId, command.userId(), "MEMBER", command.departureLocation(), command.returnDestination());
        return memberByUser(tripId, command.userId());
    }

    public Map<String, Object> get(String tripId) {
        Map<String, Object> trip = new LinkedHashMap<>(jdbc.sql("""
                SELECT id, owner_id, title, departure_mode, departure_at, meeting_at, meeting_location, status, created_at
                FROM trips WHERE id = ?
                """).param(tripId).query().listOfRows().stream().findFirst().orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다.")));
        trip.put("members", members(tripId));
        return trip;
    }

    public List<Map<String, Object>> members(String tripId) {
        requireTrip(tripId);
        return jdbc.sql("""
                SELECT tm.id, tm.user_id, u.nickname, tm.role, tm.participation_status,
                       tm.departure_location, tm.return_destination, tm.route_preferences
                FROM trip_members tm JOIN users u ON u.id = tm.user_id
                WHERE tm.trip_id = ? ORDER BY tm.created_at
                """).param(tripId).query().listOfRows();
    }

    @Transactional
    public Map<String, Object> start(String tripId) {
        transition(tripId, TripStatus.READY, TripStatus.IN_PROGRESS);
        return get(tripId);
    }

    @Transactional
    public Map<String, Object> complete(String tripId) {
        Map<String, Object> trip = get(tripId);
        TripStatus current = TripStatus.valueOf(rowValue(trip, "status").toString());
        if (current != TripStatus.IN_PROGRESS && current != TripStatus.RETURNING) {
            throw new ApiException(HttpStatus.CONFLICT, "진행 중인 여행만 완료할 수 있습니다.");
        }
        jdbc.sql("UPDATE trips SET status = 'COMPLETED' WHERE id = ?").param(tripId).update();
        return get(tripId);
    }

    public void markReady(String tripId) {
        jdbc.sql("UPDATE trips SET status = 'READY' WHERE id = ? AND status IN ('DRAFT', 'READY')")
                .param(tripId).update();
    }

    public void requireMember(String tripId, String userId) {
        int count = jdbc.sql("SELECT COUNT(*) FROM trip_members WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId).query(Integer.class).single();
        if (count == 0) throw new ApiException(HttpStatus.FORBIDDEN, "여행 멤버만 수행할 수 있습니다.");
    }

    public Map<String, Object> requireTrip(String tripId) {
        return jdbc.sql("SELECT * FROM trips WHERE id = ?").param(tripId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private void validateDeparture(DepartureMode mode, LocalDateTime meetingAt, Location meetingLocation) {
        if (mode == DepartureMode.GROUP_MEETING && (meetingAt == null || meetingLocation == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "모여서 출발하는 여행은 집결 시각과 장소가 필요합니다.");
        }
    }

    private void transition(String tripId, TripStatus expected, TripStatus target) {
        int updated = jdbc.sql("UPDATE trips SET status = ? WHERE id = ? AND status = ?")
                .params(target.name(), tripId, expected.name()).update();
        if (updated == 0) throw new ApiException(HttpStatus.CONFLICT, expected + " 상태에서만 전환할 수 있습니다.");
    }

    private void addMemberInternal(String tripId, String userId, String role, Location departure, Location returning) {
        String id = UUID.randomUUID().toString();
        try {
            jdbc.sql("""
                    INSERT INTO trip_members(id, trip_id, user_id, role, departure_location, return_destination, route_preferences)
                    VALUES (?, ?, ?, ?, ?, ?, '{}')
                    """).params(id, tripId, userId, role, json.write(departure), json.write(returning)).update();
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 참여 중인 멤버입니다.");
        }
    }

    private Map<String, Object> memberByUser(String tripId, String userId) {
        return jdbc.sql("SELECT * FROM trip_members WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId).query().singleRow();
    }

    private String nullableJson(Object value) {
        return value == null ? null : json.write(value);
    }

    private Object rowValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase());
    }

    public record CreateTrip(String ownerId, String title, DepartureMode departureMode,
                             LocalDateTime departureAt, LocalDateTime meetingAt, Location meetingLocation,
                             Location ownerDeparture, Location ownerReturn) {
    }

    public record AddMember(String userId, Location departureLocation, Location returnDestination) {
    }
}
