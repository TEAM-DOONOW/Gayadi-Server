package com.gayadi.server.invitation;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.invitation.model.InvitationStatus;
import com.gayadi.server.invitation.query.InvitationQueryResult;
import com.gayadi.server.invitation.query.InvitationJoinQueryResult;
import com.gayadi.server.invitation.query.InvitationTripQueryResult;
import com.gayadi.server.travel.model.TripStatus;
import com.gayadi.server.common.KeyHelper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 여행 초대 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class InvitationRepository {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public InvitationRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 여행 대상과 만료 정책을 포함한 새 초대를 저장합니다. */
    public long create(
            long tripId,
            long inviterId,
            Long inviteeUserId,
            String code,
            LocalDateTime expiresAt) {
        return keyHelper.insert("""
                INSERT INTO travel_invitations
                    (trip_id, inviter_id, invitee_user_id, invite_code, status, expires_at)
                VALUES (?, ?, ?, ?, 'PENDING', ?)
                """, tripId, inviterId, inviteeUserId, code, expiresAt);
    }

    /** 참여 후보 코드 정보를 DB에서 조회합니다. */
    public Optional<InvitationJoinQueryResult> findJoinCandidateByCode(String code) {
        return jdbc.sql("""
                SELECT id, trip_id, invitee_user_id, status, expires_at
                FROM travel_invitations
                WHERE invite_code = ?
                """)
                .param(code)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapJoinCandidate);
    }

    /** 후보 조건에 맞는 여행 초대 데이터를 DB에서 조회합니다. */
    public Optional<InvitationJoinQueryResult> findJoinCandidate(long invitationId, long tripId) {
        return jdbc.sql("""
                SELECT id, trip_id, invitee_user_id, status, expires_at
                FROM travel_invitations
                WHERE id = ? AND trip_id = ?
                """)
                .params(invitationId, tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapJoinCandidate);
    }

    /** 여행 식별자 재사용 코드 정보를 DB에서 조회합니다. */
    public Optional<Long> findTripIdByReusableCode(String code) {
        return jdbc.sql("""
                SELECT id FROM trips
                WHERE invite_code = ? AND deleted_at IS NULL
                """)
                .param(code)
                .query(Long.class)
                .optional();
    }

    /** 변경 충돌을 막기 위해 여행 DB 행을 잠급니다. */
    public Optional<InvitationTripQueryResult> lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT id, status, max_members
                FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(row -> new InvitationTripQueryResult(
                        RowSupport.longValue(row, "id"),
                        TripStatus.valueOf(RowSupport.strValue(row, "status")),
                        nullableInteger(row, "max_members")));
    }

    /** 참여 중 참여자 여부나 개수를 DB에서 확인합니다. */
    public boolean isJoinedMember(long tripId, long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Integer.class)
                .single() > 0;
    }

    /** DB에서 참여 중 참여자 목록 개수를 집계합니다. */
    public int countJoinedMembers(long tripId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED'
                """)
                .param(tripId)
                .query(Integer.class)
                .single();
    }

    /** 코드에 대한 여행 초대 기능을 처리합니다. */
    public boolean codeExists(String code) {
        return jdbc.sql("""
                SELECT
                  (SELECT COUNT(*) FROM travel_invitations WHERE invite_code = ?) +
                  (SELECT COUNT(*) FROM trips WHERE invite_code = ?)
                """)
                .params(code, code)
                .query(Long.class)
                .single() > 0;
    }

    /** 여행 초대 상태나 값을 DB에 반영합니다. */
    public boolean accept(long userId, long invitationId, String code) {
        return jdbc.sql("""
                UPDATE travel_invitations
                SET status = 'ACCEPTED', invitee_user_id = ?, accepted_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND invite_code = ? AND status = 'PENDING'
                  AND expires_at > CURRENT_TIMESTAMP
                  AND (invitee_user_id IS NULL OR invitee_user_id = ?)
                """)
                .params(userId, invitationId, code, userId)
                .update() == 1;
    }

    /** 결정 상태나 값을 DB에 반영합니다. */
    public boolean updateDecision(
            long invitationId,
            long tripId,
            String status,
            String timeColumn) {
        String sql = """
                UPDATE travel_invitations
                SET status = ?, %s = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND status = 'PENDING'
                  AND expires_at > CURRENT_TIMESTAMP
                """.formatted(timeColumn);
        return jdbc.sql(sql)
                .params(status, invitationId, tripId)
                .update() == 1;
    }

    /** 전체 조건에 맞는 여행 초대 데이터를 DB에서 조회합니다. */
    public List<InvitationQueryResult> findAll(long tripId, int limit, int offset) {

        return jdbc.sql("""
                SELECT i.id, i.trip_id, i.inviter_id, inviter.nickname AS inviter_nickname,
                       i.invitee_user_id, invitee.nickname AS invitee_nickname,
                       i.invite_code, i.status, i.expires_at, i.accepted_at,
                       i.rejected_at, i.canceled_at, i.created_at
                FROM travel_invitations i
                JOIN users inviter ON inviter.id = i.inviter_id
                LEFT JOIN users invitee ON invitee.id = i.invitee_user_id
                WHERE i.trip_id = ?
                ORDER BY i.created_at DESC, i.id DESC
                LIMIT ? OFFSET ?
                """)
                .params(tripId, limit, offset)
                .query()
                .listOfRows()
                .stream()
                .map(this::map)
                .toList();

    }

    /** 식별자와 여행에 해당하는 초대 상세를 조회합니다. */
    public Optional<InvitationQueryResult> find(long invitationId, long tripId) {
        return jdbc.sql("""
                SELECT i.id, i.trip_id, i.inviter_id, inviter.nickname AS inviter_nickname,
                       i.invitee_user_id, invitee.nickname AS invitee_nickname,
                       i.invite_code, i.status, i.expires_at, i.accepted_at,
                       i.rejected_at, i.canceled_at, i.created_at
                FROM travel_invitations i
                JOIN users inviter ON inviter.id = i.inviter_id
                LEFT JOIN users invitee ON invitee.id = i.invitee_user_id
                WHERE i.id = ? AND i.trip_id = ?
                """)
                .params(invitationId, tripId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::map);
    }

    /** 대기 중 여행 초대 상태를 DB에서 만료 또는 해제합니다. */
    public void expirePending(long tripId) {
        jdbc.sql("""
                UPDATE travel_invitations
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND status = 'PENDING' AND expires_at <= CURRENT_TIMESTAMP
                """)
                .param(tripId)
                .update();
    }

    private InvitationQueryResult map(Map<String, Object> row) {
        return new InvitationQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "trip_id"),
                RowSupport.longValue(row, "inviter_id"),
                nullableText(row, "inviter_nickname"),
                nullableLong(row, "invitee_user_id"),
                nullableText(row, "invitee_nickname"),
                RowSupport.strValue(row, "invite_code"),
                InvitationStatus.fromDatabase(RowSupport.strValue(row, "status")),
                dateTime(row, "expires_at"),
                nullableDateTime(row, "accepted_at"),
                nullableDateTime(row, "rejected_at"),
                nullableDateTime(row, "canceled_at"),
                dateTime(row, "created_at"));
    }

    private InvitationJoinQueryResult mapJoinCandidate(Map<String, Object> row) {
        return new InvitationJoinQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "trip_id"),
                nullableLong(row, "invitee_user_id"),
                RowSupport.strValue(row, "status"),
                dateTime(row, "expires_at"));
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : ((Number) value).longValue();
    }

    private Integer nullableInteger(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : ((Number) value).intValue();
    }

    private LocalDateTime nullableDateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        return AppDateFormat.databaseDateTime(RowSupport.value(row, key));
    }
}
