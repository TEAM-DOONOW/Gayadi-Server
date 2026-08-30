package com.gayadi.server.coordination;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.TripErrorCode;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DateCoordinationService {

    private static final int MAX_FUTURE_YEARS = 2;

    private final JdbcClient jdbc;
    private final TripService trips;

    public DateCoordinationService(JdbcClient jdbc, TripService trips) {
        this.jdbc = jdbc;
        this.trips = trips;
    }

    public DateCoordinationResponse get(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        return response(actorId, tripId);
    }

    @Transactional
    public DateCoordinationResponse submit(long actorId, long tripId, List<String> values) {
        lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        List<LocalDate> dates = normalizeDates(values);

        int updated = jdbc.sql("""
                UPDATE trip_date_availability_submissions
                SET submitted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND user_id = ?
                """)
                .params(tripId, actorId)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO trip_date_availability_submissions (trip_id, user_id)
                    VALUES (?, ?)
                    """)
                    .params(tripId, actorId)
                    .update();
        }
        jdbc.sql("DELETE FROM trip_date_availability WHERE trip_id = ? AND user_id = ?")
                .params(tripId, actorId)
                .update();
        for (LocalDate date : dates) {
            jdbc.sql("""
                    INSERT INTO trip_date_availability (trip_id, user_id, available_date)
                    VALUES (?, ?, ?)
                    """)
                    .params(tripId, actorId, date)
                    .update();
        }
        return response(actorId, tripId);
    }

    @Transactional
    public DateCoordinationResponse finalizeDates(
            long actorId, long tripId, String startValue, String endValue) {
        lockTrip(tripId);
        trips.requireOwner(tripId, actorId);
        LocalDate start = AppDateFormat.parseDate(startValue, "여행 시작일");
        LocalDate end = AppDateFormat.parseDate(endValue, "여행 종료일");
        if (start == null || end == null || end.isBefore(start)) {
            throw new BusinessException(CoordinationErrorCode.COORDINATION_DATE_RANGE_INVALID);
        }

        int memberCount = joinedMemberCount(tripId);
        if (memberCount > 1) {
            if (submittedMemberCount(tripId) != memberCount) {
                throw new BusinessException(CoordinationErrorCode.COORDINATION_SUBMISSIONS_INCOMPLETE);
            }
            Set<LocalDate> common = new LinkedHashSet<>(commonDates(tripId, memberCount));
            List<LocalDate> range = start.datesUntil(end.plusDays(1)).toList();
            if (!common.containsAll(range)) {
                throw new BusinessException(CoordinationErrorCode.COORDINATION_RANGE_NOT_COMMON);
            }
        }
        trips.finalizeDates(actorId, tripId, start, end);
        return response(actorId, tripId);
    }

    private DateCoordinationResponse response(long actorId, long tripId) {
        Map<String, Object> trip = trips.view(tripId);
        List<Map<String, Object>> members = trips.members(tripId);
        Map<Long, List<String>> datesByUser = availabilityByUser(tripId);
        Set<Long> submittedUsers = submittedUsers(tripId);
        int memberCount = members.size();
        List<String> common = commonDates(tripId, memberCount).stream()
                .map(AppDateFormat::date)
                .toList();
        boolean owner = RowSupport.longValue(trip, "ownerId") == actorId;
        boolean canFinalize = owner
                && (memberCount == 1 || submittedUsers.size() == memberCount && !common.isEmpty());
        List<DateCoordinationResponse.ParticipantAvailability> participants = members.stream()
                .map(member -> {
                    long userId = RowSupport.longValue(member, "userId");
                    return new DateCoordinationResponse.ParticipantAvailability(
                            userId,
                            RowSupport.strValue(member, "nickname"),
                            nullableString(member, "characterKey"),
                            submittedUsers.contains(userId),
                            datesByUser.getOrDefault(userId, List.of()));
                })
                .toList();
        return new DateCoordinationResponse(
                tripId,
                RowSupport.strValue(trip, "startDate"),
                RowSupport.strValue(trip, "endDate"),
                RowSupport.intValue(trip, "version"),
                canFinalize,
                common,
                participants);
    }

    private List<LocalDate> normalizeDates(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(CoordinationErrorCode.COORDINATION_AVAILABILITY_REQUIRED);
        }
        LocalDate today = LocalDate.now();
        LocalDate maximum = today.plusYears(MAX_FUTURE_YEARS);
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (String value : values) {
            LocalDate date = AppDateFormat.parseDate(value, "가능한 날짜");
            if (date.isBefore(today) || date.isAfter(maximum)) {
                throw new BusinessException(
                        CoordinationErrorCode.COORDINATION_AVAILABILITY_DATE_OUT_OF_RANGE);
            }
            dates.add(date);
        }
        return dates.stream().sorted().toList();
    }

    private Map<Long, List<String>> availabilityByUser(long tripId) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT user_id, available_date
                FROM trip_date_availability
                WHERE trip_id = ?
                ORDER BY user_id, available_date
                """)
                .param(tripId)
                .query().listOfRows()
                .forEach(row -> result
                        .computeIfAbsent(RowSupport.longValue(row, "user_id"), ignored -> new ArrayList<>())
                        .add(AppDateFormat.date(localDate(RowSupport.value(row, "available_date")))));
        return result;
    }

    private Set<Long> submittedUsers(long tripId) {
        return new LinkedHashSet<>(jdbc.sql("""
                SELECT s.user_id
                FROM trip_date_availability_submissions s
                JOIN trip_participants p ON p.trip_id = s.trip_id AND p.user_id = s.user_id
                WHERE s.trip_id = ? AND p.status = 'JOINED'
                ORDER BY s.user_id
                """)
                .param(tripId)
                .query(Long.class)
                .list());
    }

    private List<LocalDate> commonDates(long tripId, int memberCount) {
        if (memberCount == 0 || submittedMemberCount(tripId) != memberCount) return List.of();
        return jdbc.sql("""
                SELECT a.available_date
                FROM trip_date_availability a
                JOIN trip_participants p ON p.trip_id = a.trip_id AND p.user_id = a.user_id
                WHERE a.trip_id = ? AND p.status = 'JOINED'
                GROUP BY a.available_date
                HAVING COUNT(DISTINCT a.user_id) = ?
                ORDER BY a.available_date
                """)
                .params(tripId, memberCount)
                .query(LocalDate.class)
                .list();
    }

    private int joinedMemberCount(long tripId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED'
                """)
                .param(tripId)
                .query(Integer.class)
                .single();
    }

    private int submittedMemberCount(long tripId) {
        return jdbc.sql("""
                SELECT COUNT(*)
                FROM trip_date_availability_submissions s
                JOIN trip_participants p ON p.trip_id = s.trip_id AND p.user_id = s.user_id
                WHERE s.trip_id = ? AND p.status = 'JOINED'
                """)
                .param(tripId)
                .query(Integer.class)
                .single();
    }

    private void lockTrip(long tripId) {
        if (jdbc.sql("SELECT id FROM trips WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(tripId).query(Long.class).optional().isEmpty()) {
            throw new BusinessException(TripErrorCode.TRIP_NOT_FOUND);
        }
    }

    private LocalDate localDate(Object value) {
        return AppDateFormat.databaseDate(value);
    }

    private String nullableString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
        return value == null ? null : value.toString();
    }
}
