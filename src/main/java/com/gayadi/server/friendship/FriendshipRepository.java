package com.gayadi.server.friendship;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.friendship.model.FriendshipStatus;
import com.gayadi.server.friendship.query.FriendshipQueryResult;
import com.gayadi.server.friendship.query.FriendshipStateQueryResult;
import com.gayadi.server.friendship.query.PublicUserQueryResult;
import com.gayadi.server.friendship.query.UserSearchQueryResult;
import com.gayadi.server.survey.SurveyService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 친구 관계 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class FriendshipRepository {

    private static final String DETAIL_SELECT = """
            SELECT f.id, f.first_user_id, f.second_user_id, f.requester_id,
                   f.blocked_by, f.status, f.version, f.decided_at,
                   f.created_at, f.updated_at,
                   u.id AS other_user_id, u.nickname, u.introduction,
                   u.profile_image_url, r.character_key, r.emoji
            FROM friendships f
            JOIN users u ON u.id = CASE
                WHEN f.first_user_id = ? THEN f.second_user_id
                ELSE f.first_user_id
            END
            LEFT JOIN survey_attempts a ON a.id = (
                SELECT sa.id FROM survey_attempts sa
                WHERE sa.user_id = u.id AND sa.survey_id = ? AND sa.status = 'COMPLETED'
                ORDER BY sa.completed_at DESC, sa.id DESC
                LIMIT 1
            )
            LEFT JOIN travel_personality_results r ON r.result_code = a.result_code
            """;

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public FriendshipRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 변경 충돌을 막기 위해 활성 사용자 DB 행을 잠급니다. */
    public List<Long> lockActiveUsers(long firstUserId, long secondUserId) {
        return jdbc.sql("""
                SELECT id FROM users
                WHERE id IN (?, ?) AND status = 'ACTIVE' AND deleted_at IS NULL
                ORDER BY id
                FOR UPDATE
                """)
                .params(firstUserId, secondUserId)
                .query(Long.class)
                .list();
    }

    /** 두 사용자 관계 정보를 DB에서 조회합니다. */
    public Optional<FriendshipStateQueryResult> findPairForUpdate(long firstUserId, long secondUserId) {
        return jdbc.sql("""
                SELECT id, first_user_id, second_user_id, requester_id, blocked_by, status, version
                FROM friendships
                WHERE first_user_id = ? AND second_user_id = ?
                FOR UPDATE
                """)
                .params(firstUserId, secondUserId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapState);
    }

    /** 친구 관계 정보를 DB에서 조회합니다. */
    public Optional<FriendshipStateQueryResult> findForUpdate(long friendshipId) {
        return jdbc.sql("""
                SELECT id, first_user_id, second_user_id, requester_id, blocked_by, status, version
                FROM friendships WHERE id = ? FOR UPDATE
                """)
                .param(friendshipId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapState);
    }

    /** 두 사용자 사이에 대기 중인 친구 요청을 저장합니다. */
    public long create(long firstUserId, long secondUserId, long requesterId) {
        return keyHelper.insert("""
                INSERT INTO friendships (first_user_id, second_user_id, requester_id, status)
                VALUES (?, ?, ?, 'PENDING')
                """, firstUserId, secondUserId, requesterId);
    }

    /** 거절 상태 상태나 값을 DB에 반영합니다. */
    public boolean reopenRejected(long friendshipId, long requesterId) {
        return jdbc.sql("""
                UPDATE friendships
                SET requester_id = ?, blocked_by = NULL, status = 'PENDING',
                    version = version + 1, decided_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'REJECTED'
                """)
                .params(requesterId, friendshipId)
                .update() == 1;
    }

    /** 상태 친구 관계 상태를 DB에 반영합니다. */
    public boolean updateStatus(
            long friendshipId,
            FriendshipStatus currentStatus,
            FriendshipStatus targetStatus,
            Long blockedBy,
            int version) {
        return jdbc.sql("""
                UPDATE friendships
                SET status = ?, blocked_by = ?, version = version + 1,
                    decided_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ? AND status = ?
                """)
                .params(targetStatus.name(), blockedBy, friendshipId, version, currentStatus.name())
                .update() == 1;
    }

    /** 버전이 일치하는 친구 관계를 삭제합니다. */
    public boolean delete(long friendshipId, int version) {
        return jdbc.sql("DELETE FROM friendships WHERE id = ? AND version = ?")
                .params(friendshipId, version)
                .update() == 1;
    }

    /** 상세 정보를 DB에서 조회합니다. */
    public Optional<FriendshipQueryResult> findDetail(long actorId, long friendshipId) {
        return jdbc.sql(DETAIL_SELECT + """
                WHERE f.id = ? AND (f.first_user_id = ? OR f.second_user_id = ?)
                """)
                .params(actorId, SurveyService.PERSONALITY_SURVEY_ID,
                        friendshipId, actorId, actorId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapFriendship);
    }

    /** 전체 조건에 맞는 친구 관계 데이터를 DB에서 조회합니다. */
    public List<FriendshipQueryResult> findAll(
            long userId,
            FriendshipStatus status,
            int limit,
            int offset) {
        String statusClause = status == null ? "" : " AND f.status = ?";
        String sql = DETAIL_SELECT + """
                WHERE (f.first_user_id = ? OR f.second_user_id = ?)
                  AND (f.status <> 'BLOCKED' OR f.blocked_by = ?)
                """ + statusClause + """
                ORDER BY CASE f.status
                    WHEN 'PENDING' THEN 0 WHEN 'ACCEPTED' THEN 1
                    WHEN 'REJECTED' THEN 2 ELSE 3
                END, f.updated_at DESC, f.id DESC
                LIMIT ? OFFSET ?
                """;
        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        parameters.add(SurveyService.PERSONALITY_SURVEY_ID);
        parameters.add(userId);
        parameters.add(userId);
        parameters.add(userId);
        if (status != null) {
            parameters.add(status.name());
        }
        parameters.add(limit);
        parameters.add(offset);
        return jdbc.sql(sql)
                .params(parameters)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapFriendship)
                .toList();
    }

    /** 사용자 조건에 맞는 친구 관계 데이터를 DB에서 조회합니다. */
    public List<UserSearchQueryResult> searchUsers(long userId, String query, int limit) {

        return jdbc.sql("""
                SELECT u.id, u.nickname, u.introduction, u.profile_image_url,
                       r.character_key, r.emoji,
                       f.id AS friendship_id, f.status AS friendship_status,
                       f.requester_id, f.version AS friendship_version
                FROM users u
                LEFT JOIN friendships f
                  ON f.first_user_id = CASE WHEN u.id < ? THEN u.id ELSE ? END
                 AND f.second_user_id = CASE WHEN u.id < ? THEN ? ELSE u.id END
                LEFT JOIN survey_attempts a ON a.id = (
                    SELECT sa.id FROM survey_attempts sa
                    WHERE sa.user_id = u.id AND sa.survey_id = ? AND sa.status = 'COMPLETED'
                    ORDER BY sa.completed_at DESC, sa.id DESC LIMIT 1
                )
                LEFT JOIN travel_personality_results r ON r.result_code = a.result_code
                WHERE u.id <> ? AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                  AND POSITION(LOWER(?) IN LOWER(u.nickname)) > 0
                  AND (f.id IS NULL OR f.status <> 'BLOCKED')
                ORDER BY CASE f.status WHEN 'ACCEPTED' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END,
                         u.nickname, u.id
                LIMIT ?
                """)
                .params(userId, userId, userId, userId,
                        SurveyService.PERSONALITY_SURVEY_ID, userId, query, limit)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapSearch)
                .toList();

    }

    private FriendshipStateQueryResult mapState(Map<String, Object> row) {
        return new FriendshipStateQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "first_user_id"),
                RowSupport.longValue(row, "second_user_id"),
                RowSupport.longValue(row, "requester_id"),
                nullableLong(row, "blocked_by"),
                FriendshipStatus.valueOf(RowSupport.strValue(row, "status")),
                RowSupport.intValue(row, "version"));
    }

    private FriendshipQueryResult mapFriendship(Map<String, Object> row) {
        return new FriendshipQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.longValue(row, "requester_id"),
                nullableLong(row, "blocked_by"),
                FriendshipStatus.valueOf(RowSupport.strValue(row, "status")),
                RowSupport.intValue(row, "version"),
                nullableDateTime(row, "decided_at"),
                nullableDateTime(row, "created_at"),
                nullableDateTime(row, "updated_at"),
                publicUser(row, "other_user_id"));
    }

    private UserSearchQueryResult mapSearch(Map<String, Object> row) {
        String status = nullableText(row, "friendship_status");
        return new UserSearchQueryResult(
                publicUser(row, "id"),
                nullableLong(row, "friendship_id"),
                status == null ? null : FriendshipStatus.valueOf(status),
                nullableLong(row, "requester_id"),
                nullableInteger(row, "friendship_version"));
    }

    private PublicUserQueryResult publicUser(Map<String, Object> row, String idColumn) {
        return new PublicUserQueryResult(
                RowSupport.longValue(row, idColumn),
                RowSupport.strValue(row, "nickname"),
                nullableText(row, "introduction"),
                nullableText(row, "profile_image_url"),
                nullableText(row, "character_key"),
                nullableText(row, "emoji"));
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
}
