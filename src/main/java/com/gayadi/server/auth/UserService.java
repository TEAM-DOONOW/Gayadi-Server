package com.gayadi.server.auth;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.survey.SurveyService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private static final String USER_COLUMNS = """
            id, nickname, email, introduction, profile_image_url, status,
            last_login_at, created_at, updated_at
            """;

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;
    private final JsonSupport json;

    public UserService(JdbcClient jdbc, KeyHelper keyHelper, JsonSupport json) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
        this.json = json;
    }

    public Map<String, Object> create(String nickname) {
        long id = keyHelper.insert("INSERT INTO users (nickname) VALUES (?)", nickname.trim());
        return get(id);
    }

    public Map<String, Object> get(long id) {
        return findBy("id", id)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    public Optional<Map<String, Object>> findByEmail(String email) {
        return findBy("email", email);
    }

    public Optional<Map<String, Object>> findById(long id) {
        return findBy("id", id);
    }

    public Map<String, Object> profile(long id) {
        Map<String, Object> user = get(id);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.get("id"));
        profile.put("email", user.get("email"));
        profile.put("nickname", user.get("nickname"));
        profile.put("introduction", user.get("introduction"));
        profile.put("profileImageUrl", user.get("profile_image_url"));

        jdbc.sql("""
                SELECT a.result_code, r.name, r.character_key, r.strengths, r.weaknesses
                FROM survey_attempts a
                LEFT JOIN travel_personality_results r ON r.result_code = a.result_code
                WHERE a.user_id = ? AND a.survey_id = ? AND a.status = 'COMPLETED'
                ORDER BY a.completed_at DESC, a.id DESC
                LIMIT 1
                """)
                .params(id, SurveyService.PERSONALITY_SURVEY_ID)
                .query().listOfRows().stream().findFirst()
                .ifPresent(result -> {
                    profile.put("resultCode", value(result, "result_code"));
                    profile.put("travelStyleName", value(result, "name"));
                    profile.put("characterKey", value(result, "character_key"));
                    profile.put("strengths", jsonList(value(result, "strengths")));
                    profile.put("weaknesses", jsonList(value(result, "weaknesses")));
                });
        return profile;
    }

    @Transactional
    public Map<String, Object> update(long id, String nickname, String introduction) {
        int updated = jdbc.sql("""
                UPDATE users
                SET nickname = ?, introduction = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .params(nickname.trim(), trimToNull(introduction), id)
                .update();
        if (updated == 0) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        return profile(id);
    }

    @Transactional
    public void withdraw(long id) {
        lockActive(id);
        long ownedTrips = jdbc.sql("""
                SELECT COUNT(*) FROM trips
                WHERE owner_id = ? AND deleted_at IS NULL
                  AND status IN ('PLANNING', 'IN_PROGRESS')
                """)
                .param(id)
                .query(Long.class)
                .single();
        if (ownedTrips > 0) {
            throw new BusinessException(UserErrorCode.USER_ACTIVE_OWNED_TRIP_EXISTS);
        }
        // 다른 사용자 자료가 탈퇴자의 초대·경로를 가리키는 경우도 먼저 끊는다.
        jdbc.sql("""
                DELETE FROM notifications
                WHERE user_id = ?
                   OR invitation_id IN (
                       SELECT id FROM travel_invitations
                       WHERE inviter_id = ? OR invitee_user_id = ?)
                   OR route_id IN (
                       SELECT r.id FROM travel_routes r
                       JOIN trip_participants tp ON tp.id = r.member_id
                       WHERE tp.user_id = ?)
                """)
                .params(id, id, id, id)
                .update();
        jdbc.sql("DELETE FROM user_devices WHERE user_id = ?").param(id).update();
        jdbc.sql("DELETE FROM social_login_accounts WHERE user_id = ?").param(id).update();
        jdbc.sql("DELETE FROM user_favorite_places WHERE user_id = ?").param(id).update();
        jdbc.sql("DELETE FROM friendships WHERE first_user_id = ? OR second_user_id = ?")
                .params(id, id).update();
        jdbc.sql("DELETE FROM survey_attempts WHERE user_id = ?").param(id).update();
        jdbc.sql("DELETE FROM travel_invitations WHERE inviter_id = ? OR invitee_user_id = ?")
                .params(id, id).update();
        jdbc.sql("UPDATE ai_schedule_change_proposals SET decided_by = NULL WHERE decided_by = ?")
                .param(id).update();
        jdbc.sql("UPDATE travel_supplies SET checked_by = NULL WHERE checked_by = ?")
                .param(id).update();
        jdbc.sql("""
                UPDATE travel_plans p
                SET created_by = (SELECT t.owner_id FROM trips t WHERE t.id = p.trip_id),
                    updated_at = CURRENT_TIMESTAMP
                WHERE p.created_by = ?
                  AND (SELECT t.owner_id FROM trips t WHERE t.id = p.trip_id) <> ?
                """)
                .params(id, id).update();
        jdbc.sql("""
                UPDATE travel_supplies s
                SET created_by = (SELECT t.owner_id FROM trips t WHERE t.id = s.trip_id),
                    updated_at = CURRENT_TIMESTAMP
                WHERE s.created_by = ?
                  AND (SELECT t.owner_id FROM trips t WHERE t.id = s.trip_id) <> ?
                """)
                .params(id, id).update();
        jdbc.sql("""
                DELETE FROM notifications
                WHERE route_id IN (
                    SELECT DISTINCT r.id
                    FROM travel_routes r
                    JOIN travel_plan_items item ON item.plan_id = r.plan_id
                    JOIN places place ON place.id = item.place_id
                    WHERE place.owner_user_id = ?)
                """)
                .param(id).update();
        jdbc.sql("""
                DELETE FROM travel_routes
                WHERE plan_id IN (
                    SELECT DISTINCT item.plan_id
                    FROM travel_plan_items item
                    JOIN places place ON place.id = item.place_id
                    WHERE place.owner_user_id = ?)
                """)
                .param(id).update();
        jdbc.sql("""
                UPDATE places
                SET owner_user_id = NULL, visibility = 'PRIVATE', status = 'DELETED',
                    source_place_id = NULL, name = '삭제된 장소', address = NULL,
                    road_address = NULL, latitude = 0, longitude = 0, phone = NULL,
                    homepage_url = NULL, image_url = NULL, basic_info = NULL,
                    operating_hours = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE owner_user_id = ?
                """)
                .param(id).update();
        jdbc.sql("""
                UPDATE travel_supplies
                SET assigned_member_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE assigned_member_id IN (
                    SELECT id FROM trip_participants WHERE user_id = ?)
                """)
                .param(id).update();
        jdbc.sql("""
                DELETE FROM travel_routes
                WHERE member_id IN (
                    SELECT id FROM trip_participants WHERE user_id = ?)
                """)
                .param(id).update();
        jdbc.sql("""
                UPDATE trip_participants
                SET departure_place_id = NULL, return_place_id = NULL,
                    route_preferences = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                """)
                .param(id).update();
        jdbc.sql("DELETE FROM trip_participants WHERE user_id = ? AND role = 'MEMBER'")
                .param(id).update();
        int updated = jdbc.sql("""
                UPDATE users
                SET nickname = '탈퇴한 사용자',
                    email = CONCAT('withdrawn-', id, '@invalid.local'),
                    password_hash = NULL,
                    introduction = NULL,
                    profile_image_url = NULL,
                    status = 'WITHDRAW', deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .param(id)
                .update();
        if (updated == 0) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    public boolean isActive(long id) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM users
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .param(id)
                .query(Long.class)
                .optional()
                .orElse(0L) > 0;
    }

    public void requireExists(long id) {
        long count = jdbc.sql("""
                SELECT COUNT(*) FROM users
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .param(id)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
    }

    public void lockActive(long id) {
        jdbc.sql("""
                SELECT id FROM users
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(id)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private Optional<Map<String, Object>> findBy(String column, Object value) {
        return jdbc.sql("SELECT " + USER_COLUMNS + " FROM users WHERE " + column + " = ? AND deleted_at IS NULL")
                .param(value)
                .query().listOfRows().stream()
                .findFirst();
    }

    private Object value(Map<String, Object> row, String key) {
        Object result = row.get(key);
        return result != null ? result : row.get(key.toUpperCase());
    }

    private List<?> jsonList(Object value) {
        if (value == null || value.toString().isBlank()) return List.of();
        return json.read(value.toString(), List.class);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim();
    }
}
