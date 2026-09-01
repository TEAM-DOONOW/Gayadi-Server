package com.gayadi.server.coordination;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.coordination.query.DateAvailabilityQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 여행 날짜 조율 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class DateCoordinationRepository {

    private final JdbcClient jdbc;

    public DateCoordinationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 날짜 확정 충돌을 막기 위해 여행 DB 행을 잠급니다. */
    public boolean lockTrip(long tripId) {
        return jdbc.sql("SELECT id FROM trips WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(tripId)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /** 가능 날짜 정보를 새 값으로 교체합니다. */
    public void replaceAvailability(long tripId, long userId, List<LocalDate> dates) {
        int updated = jdbc.sql("""
                UPDATE trip_date_availability_submissions
                SET submitted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND user_id = ?
                """)
                .params(tripId, userId)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO trip_date_availability_submissions (trip_id, user_id) VALUES (?, ?)
                    """)
                    .params(tripId, userId)
                    .update();
        }
        jdbc.sql("DELETE FROM trip_date_availability WHERE trip_id = ? AND user_id = ?")
                .params(tripId, userId)
                .update();
        dates.forEach(date -> jdbc.sql("""
                INSERT INTO trip_date_availability (trip_id, user_id, available_date) VALUES (?, ?, ?)
                """)
                .params(tripId, userId, date)
                .update());
    }

    /** 가능 날짜 조건에 맞는 여행 날짜 조율 데이터를 DB에서 조회합니다. */
    public List<DateAvailabilityQueryResult> findAvailability(long tripId) {
        return jdbc.sql("""
                SELECT user_id, available_date FROM trip_date_availability
                WHERE trip_id = ? ORDER BY user_id, available_date
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .map(row -> new DateAvailabilityQueryResult(
                        RowSupport.longValue(row, "user_id"),
                        AppDateFormat.databaseDate(RowSupport.value(row, "available_date"))))
                .toList();
    }

    /** 제출한 사용자 정보를 DB에서 조회합니다. */
    public Set<Long> findSubmittedUsers(long tripId) {
        return new LinkedHashSet<>(jdbc.sql("""
                SELECT s.user_id FROM trip_date_availability_submissions s
                JOIN trip_participants p ON p.trip_id = s.trip_id AND p.user_id = s.user_id
                WHERE s.trip_id = ? AND p.status = 'JOINED' ORDER BY s.user_id
                """)
                .param(tripId)
                .query(Long.class)
                .list());
    }

    /** 공통 날짜 정보를 DB에서 조회합니다. */
    public List<LocalDate> findCommonDates(long tripId, int memberCount) {
        if (memberCount == 0 || submittedMemberCount(tripId) != memberCount) {
            return List.of();
        }
        return jdbc.sql("""
                SELECT a.available_date FROM trip_date_availability a
                JOIN trip_participants p ON p.trip_id = a.trip_id AND p.user_id = a.user_id
                WHERE a.trip_id = ? AND p.status = 'JOINED'
                GROUP BY a.available_date HAVING COUNT(DISTINCT a.user_id) = ?
                ORDER BY a.available_date
                """)
                .params(tripId, memberCount)
                .query(LocalDate.class)
                .list();
    }

    /** 참여 중 참여자 여행 날짜 조율 상태 전이를 처리합니다. */
    public int joinedMemberCount(long tripId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants WHERE trip_id = ? AND status = 'JOINED'
                """)
                .param(tripId)
                .query(Integer.class)
                .single();
    }

    /** 참여자 관련 날짜 조율 업무를 처리합니다. */
    public int submittedMemberCount(long tripId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_date_availability_submissions s
                JOIN trip_participants p ON p.trip_id = s.trip_id AND p.user_id = s.user_id
                WHERE s.trip_id = ? AND p.status = 'JOINED'
                """)
                .param(tripId)
                .query(Integer.class)
                .single();
    }
}
