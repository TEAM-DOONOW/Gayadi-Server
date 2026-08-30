package com.gayadi.server.friendship;

import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.survey.SurveyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class FriendshipService {

    private static final int MAX_LIST_SIZE = 100;
    private static final int MAX_SEARCH_SIZE = 30;

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public FriendshipService(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public Map<String, Object> create(long requesterId, long targetUserId) {
        if (requesterId == targetUserId) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_SELF_REQUEST);
        }
        lockActiveUsers(requesterId, targetUserId);
        long firstUserId = Math.min(requesterId, targetUserId);
        long secondUserId = Math.max(requesterId, targetUserId);

        Map<String, Object> current = lockedPair(firstUserId, secondUserId);
        if (current != null) {
            FriendshipStatus status = FriendshipStatus.valueOf(RowSupport.strValue(current, "status"));
            if (status != FriendshipStatus.REJECTED) {
                throw new BusinessException(requestConflictCode(status));
            }
            int updated = jdbc.sql("""
                    UPDATE friendships
                    SET requester_id = ?, blocked_by = NULL, status = 'PENDING',
                        version = version + 1, decided_at = NULL, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'REJECTED'
                    """)
                    .params(requesterId, RowSupport.longValue(current, "id"))
                    .update();
            if (updated == 0) {
                throw changedConflict();
            }
            return detail(requesterId, RowSupport.longValue(current, "id"));
        }

        try {
            long id = keyHelper.insert("""
                    INSERT INTO friendships
                        (first_user_id, second_user_id, requester_id, status)
                    VALUES (?, ?, ?, 'PENDING')
                    """, firstUserId, secondUserId, requesterId);
            return detail(requesterId, id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_REQUEST_CONFLICT);
        }
    }

    public List<Map<String, Object>> list(
            long userId,
            String requestedStatus,
            int requestedLimit,
            int requestedOffset) {
        FriendshipStatus status = normalizeStatus(requestedStatus);
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int offset = Math.max(0, requestedOffset);
        String statusClause = status == null ? "" : " AND f.status = ?";
        String sql = """
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
                WHERE (f.first_user_id = ? OR f.second_user_id = ?)
                  AND (f.status <> 'BLOCKED' OR f.blocked_by = ?)
                """ + statusClause + """
                ORDER BY CASE f.status
                    WHEN 'PENDING' THEN 0
                    WHEN 'ACCEPTED' THEN 1
                    WHEN 'REJECTED' THEN 2
                    ELSE 3
                END, f.updated_at DESC, f.id DESC
                LIMIT ? OFFSET ?
                """;

        List<Object> parameters = new ArrayList<>();
        parameters.add(userId);
        parameters.add(SurveyService.PERSONALITY_SURVEY_ID);
        parameters.add(userId);
        parameters.add(userId);
        parameters.add(userId);
        if (status != null) parameters.add(status.name());
        parameters.add(limit);
        parameters.add(offset);
        return jdbc.sql(sql)
                .params(parameters)
                .query().listOfRows().stream()
                .map(row -> toFriendship(row, userId))
                .toList();
    }

    @Transactional
    public Map<String, Object> update(
            long actorId,
            long friendshipId,
            FriendshipStatus targetStatus,
            Integer expectedVersion) {
        if (targetStatus == null || targetStatus == FriendshipStatus.PENDING) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_STATUS_INVALID);
        }
        Map<String, Object> current = lockedAccessible(friendshipId, actorId);
        int currentVersion = RowSupport.intValue(current, "version");
        if (expectedVersion != null && expectedVersion != currentVersion) {
            throw changedConflict();
        }
        FriendshipStatus currentStatus = FriendshipStatus.valueOf(RowSupport.strValue(current, "status"));
        long requesterId = RowSupport.longValue(current, "requester_id");

        if (targetStatus == FriendshipStatus.ACCEPTED || targetStatus == FriendshipStatus.REJECTED) {
            if (currentStatus != FriendshipStatus.PENDING) {
                throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_DECISION_NOT_PENDING);
            }
            if (actorId == requesterId) {
                throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_DECISION_FORBIDDEN);
            }
        } else if (targetStatus == FriendshipStatus.BLOCKED) {
            if (currentStatus == FriendshipStatus.BLOCKED) {
                throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_ALREADY_BLOCKED);
            }
        }

        Long blockedBy = targetStatus == FriendshipStatus.BLOCKED ? actorId : null;
        int updated = jdbc.sql("""
                UPDATE friendships
                SET status = ?, blocked_by = ?, version = version + 1,
                    decided_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND version = ? AND status = ?
                """)
                .params(targetStatus.name(), blockedBy, friendshipId,
                        currentVersion, currentStatus.name())
                .update();
        if (updated == 0) {
            throw changedConflict();
        }
        return detail(actorId, friendshipId);
    }

    @Transactional
    public void delete(long actorId, long friendshipId) {
        Map<String, Object> current = lockedAccessible(friendshipId, actorId);
        FriendshipStatus status = FriendshipStatus.valueOf(RowSupport.strValue(current, "status"));
        long requesterId = RowSupport.longValue(current, "requester_id");
        Long blockedBy = nullableLong(current, "blocked_by");

        if (status == FriendshipStatus.PENDING && requesterId != actorId) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_CANCEL_FORBIDDEN);
        }
        if (status == FriendshipStatus.BLOCKED && !Long.valueOf(actorId).equals(blockedBy)) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        int deleted = jdbc.sql("DELETE FROM friendships WHERE id = ? AND version = ?")
                .params(friendshipId, RowSupport.intValue(current, "version"))
                .update();
        if (deleted == 0) {
            throw changedConflict();
        }
    }

    public List<Map<String, Object>> searchUsers(long userId, String rawQuery, int requestedLimit) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        int limit = Math.max(1, Math.min(requestedLimit, MAX_SEARCH_SIZE));
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
                    ORDER BY sa.completed_at DESC, sa.id DESC
                    LIMIT 1
                )
                LEFT JOIN travel_personality_results r ON r.result_code = a.result_code
                WHERE u.id <> ? AND u.status = 'ACTIVE' AND u.deleted_at IS NULL
                  AND POSITION(LOWER(?) IN LOWER(u.nickname)) > 0
                  AND (f.id IS NULL OR f.status <> 'BLOCKED')
                ORDER BY CASE f.status
                    WHEN 'ACCEPTED' THEN 0
                    WHEN 'PENDING' THEN 1
                    ELSE 2
                END, u.nickname, u.id
                LIMIT ?
                """)
                .params(userId, userId, userId, userId,
                        SurveyService.PERSONALITY_SURVEY_ID, userId,
                        query.toLowerCase(Locale.ROOT), limit)
                .query().listOfRows().stream()
                .map(row -> toSearchResult(row, userId))
                .toList();
    }

    private void lockActiveUsers(long firstUserId, long secondUserId) {
        List<Long> activeUsers = jdbc.sql("""
                SELECT id FROM users
                WHERE id IN (?, ?) AND status = 'ACTIVE' AND deleted_at IS NULL
                ORDER BY id
                FOR UPDATE
                """)
                .params(firstUserId, secondUserId)
                .query(Long.class)
                .list();
        if (activeUsers.size() != 2) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_TARGET_USER_NOT_FOUND);
        }
    }

    private Map<String, Object> lockedPair(long firstUserId, long secondUserId) {
        return jdbc.sql("""
                SELECT * FROM friendships
                WHERE first_user_id = ? AND second_user_id = ?
                FOR UPDATE
                """)
                .params(firstUserId, secondUserId)
                .query().listOfRows().stream()
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> lockedAccessible(long friendshipId, long actorId) {
        Map<String, Object> row = jdbc.sql("SELECT * FROM friendships WHERE id = ? FOR UPDATE")
                .param(friendshipId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND));
        long firstUserId = RowSupport.longValue(row, "first_user_id");
        long secondUserId = RowSupport.longValue(row, "second_user_id");
        if (actorId != firstUserId && actorId != secondUserId) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        Long blockedBy = nullableLong(row, "blocked_by");
        if (FriendshipStatus.BLOCKED.name().equals(RowSupport.strValue(row, "status"))
                && !Long.valueOf(actorId).equals(blockedBy)) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND);
        }
        return row;
    }

    private Map<String, Object> detail(long actorId, long friendshipId) {
        Map<String, Object> row = jdbc.sql("""
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
                WHERE f.id = ? AND (f.first_user_id = ? OR f.second_user_id = ?)
                """)
                .params(actorId, SurveyService.PERSONALITY_SURVEY_ID,
                        friendshipId, actorId, actorId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND));
        return toFriendship(row, actorId);
    }

    private Map<String, Object> toFriendship(Map<String, Object> row, long actorId) {
        long requesterId = RowSupport.longValue(row, "requester_id");
        FriendshipStatus status = FriendshipStatus.valueOf(RowSupport.strValue(row, "status"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", RowSupport.longValue(row, "id"));
        result.put("user", publicUser(row, "other_user_id"));
        result.put("status", status.name());
        result.put("requestedByMe", requesterId == actorId);
        result.put("canDecide", status == FriendshipStatus.PENDING && requesterId != actorId);
        result.put("blockedByMe", status == FriendshipStatus.BLOCKED
                && Long.valueOf(actorId).equals(nullableLong(row, "blocked_by")));
        result.put("version", RowSupport.intValue(row, "version"));
        result.put("decidedAt", nullable(row, "decided_at"));
        result.put("createdAt", nullable(row, "created_at"));
        result.put("updatedAt", nullable(row, "updated_at"));
        return result;
    }

    private Map<String, Object> toSearchResult(Map<String, Object> row, long actorId) {
        Map<String, Object> result = publicUser(row, "id");
        Object friendshipId = nullable(row, "friendship_id");
        result.put("friendshipId", friendshipId);
        result.put("friendshipStatus", nullable(row, "friendship_status"));
        Object requesterId = nullable(row, "requester_id");
        result.put("requestedByMe", requesterId instanceof Number number && number.longValue() == actorId);
        result.put("friendshipVersion", nullable(row, "friendship_version"));
        return result;
    }

    private Map<String, Object> publicUser(Map<String, Object> row, String idColumn) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", RowSupport.longValue(row, idColumn));
        user.put("nickname", RowSupport.strValue(row, "nickname"));
        user.put("introduction", nullable(row, "introduction"));
        user.put("profileImageUrl", nullable(row, "profile_image_url"));
        user.put("characterKey", nullable(row, "character_key"));
        user.put("emoji", nullable(row, "emoji"));
        return user;
    }

    private FriendshipStatus normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try {
            return FriendshipStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(FriendshipErrorCode.FRIENDSHIP_STATUS_INVALID);
        }
    }

    private FriendshipErrorCode requestConflictCode(FriendshipStatus status) {
        return switch (status) {
            case PENDING -> FriendshipErrorCode.FRIENDSHIP_REQUEST_PENDING;
            case ACCEPTED -> FriendshipErrorCode.FRIENDSHIP_ALREADY_ACCEPTED;
            case BLOCKED -> FriendshipErrorCode.FRIENDSHIP_REQUEST_BLOCKED;
            case REJECTED -> throw new IllegalStateException("거절된 요청은 다시 보낼 수 있습니다.");
        };
    }

    private BusinessException changedConflict() {
        return new BusinessException(FriendshipErrorCode.FRIENDSHIP_CHANGED);
    }

    private Object nullable(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = nullable(row, key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(value.toString());
    }
}
