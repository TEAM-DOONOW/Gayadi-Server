package com.gayadi.server.auth;

import com.gayadi.server.auth.model.UserStatus;
import com.gayadi.server.auth.query.UserPersonalityQueryResult;
import com.gayadi.server.auth.query.UserQueryResult;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 인증과 사용자 계정 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class UserRepository {
    private static final long PERSONALITY_SURVEY_ID = 2L;
    private static final String USER_COLUMNS = """
            id, nickname, email, introduction, profile_image_url, status,
            last_login_at, created_at, updated_at
            """;
    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;
    private final JsonSupport json;

    public UserRepository(JdbcClient jdbc, KeyHelper keyHelper, JsonSupport json) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
        this.json = json;
    }

    /** 사용자 사용자 데이터를 DB에 저장합니다. */
    public long create(String nickname) {
        return keyHelper.insert("INSERT INTO users (nickname) VALUES (?)", nickname);
    }

    /** 식별자 정보를 DB에서 조회합니다. */
    public Optional<UserQueryResult> findById(long id) {
        return findBy("id", id);
    }

    /** 이메일 정보를 DB에서 조회합니다. */
    public Optional<UserQueryResult> findByEmail(String email) {
        return findBy("email", email);
    }

    /** 최근 성향 정보를 DB에서 조회합니다. */
    public Optional<UserPersonalityQueryResult> findLatestPersonality(long userId) {
        return jdbc.sql("""
                SELECT a.result_code, r.name, r.character_key, r.strengths, r.weaknesses
                FROM survey_attempts a
                LEFT JOIN travel_personality_results r ON r.result_code = a.result_code
                WHERE a.user_id = ? AND a.survey_id = ? AND a.status = 'COMPLETED'
                ORDER BY a.completed_at DESC, a.id DESC LIMIT 1
                """)
                .params(userId, PERSONALITY_SURVEY_ID)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapPersonality);
    }

    /** 성향 사용자 상태를 DB에 반영합니다. */
    public boolean updateProfile(long id, String nickname, String introduction) {
        return jdbc.sql("""
                UPDATE users SET nickname = ?, introduction = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .params(nickname, introduction, id)
                .update() == 1;
    }

    /** 활성 여부나 개수를 DB에서 확인합니다. */
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

    /** 변경 충돌을 막기 위해 활성 DB 행을 잠급니다. */
    public boolean lockActive(long id) {
        return jdbc.sql("""
                SELECT id FROM users
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL FOR UPDATE
                """)
                .param(id)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /** 활성 소유 여행 여부나 개수를 DB에서 확인합니다. */
    public long countActiveOwnedTrips(long id) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trips
                WHERE owner_id = ? AND deleted_at IS NULL
                  AND status IN ('PLANNING', 'IN_PROGRESS')
                """)
                .param(id)
                .query(Long.class)
                .single();
    }

    /** 사용자 연관 데이터 정보를 DB에서 삭제합니다. */
    public void removeUserRelations(long id) {
        // 사용자에게 직접 귀속된 알림·인증·관계·설문 데이터를 먼저 제거합니다.
        jdbc.sql("""
                DELETE FROM notifications
                WHERE user_id = ? OR invitation_id IN (
                    SELECT id FROM travel_invitations WHERE inviter_id = ? OR invitee_user_id = ?)
                   OR route_id IN (
                    SELECT r.id FROM travel_routes r JOIN trip_participants tp ON tp.id = r.member_id
                    WHERE tp.user_id = ?)
                """)
                .params(id, id, id, id)
                .update();
        jdbc.sql("DELETE FROM user_devices WHERE user_id = ?")
        .param(id)
        .update();
        jdbc.sql("DELETE FROM social_login_accounts WHERE user_id = ?")
        .param(id)
        .update();
        jdbc.sql("DELETE FROM user_favorite_places WHERE user_id = ?")
        .param(id)
        .update();
        jdbc.sql("DELETE FROM friendships WHERE first_user_id = ? OR second_user_id = ?")
        .params(id, id)
        .update();
        jdbc.sql("DELETE FROM survey_attempts WHERE user_id = ?")
        .param(id)
        .update();
        jdbc.sql("DELETE FROM travel_invitations WHERE inviter_id = ? OR invitee_user_id = ?")
        .params(id, id)
        .update();

        // 공유 여행 데이터는 삭제하지 않고 탈퇴 사용자 참조를 소유자 또는 NULL로 치환합니다.
        jdbc.sql("UPDATE ai_schedule_change_proposals SET decided_by = NULL WHERE decided_by = ?")
        .param(id)
        .update();
        jdbc.sql("UPDATE travel_supplies SET checked_by = NULL WHERE checked_by = ?")
        .param(id)
        .update();
        jdbc.sql("""
                UPDATE travel_plans p
                SET created_by = (SELECT t.owner_id FROM trips t WHERE t.id = p.trip_id),
                    updated_at = CURRENT_TIMESTAMP
                WHERE p.created_by = ? AND (SELECT t.owner_id FROM trips t WHERE t.id = p.trip_id) <> ?
                """)
                .params(id, id)
                .update();
        jdbc.sql("""
                UPDATE travel_supplies s
                SET created_by = (SELECT t.owner_id FROM trips t WHERE t.id = s.trip_id),
                    updated_at = CURRENT_TIMESTAMP
                WHERE s.created_by = ? AND (SELECT t.owner_id FROM trips t WHERE t.id = s.trip_id) <> ?
                """)
                .params(id, id)
                .update();

        // 사용자 소유 장소에 의존하는 경로를 제거한 뒤 장소 자체는 익명화합니다.
        jdbc.sql("""
                DELETE FROM notifications WHERE route_id IN (
                    SELECT DISTINCT r.id FROM travel_routes r
                    JOIN travel_plan_items item ON item.plan_id = r.plan_id
                    JOIN places place ON place.id = item.place_id WHERE place.owner_user_id = ?)
                """)
                .param(id)
                .update();
        jdbc.sql("""
                DELETE FROM travel_routes WHERE plan_id IN (
                    SELECT DISTINCT item.plan_id FROM travel_plan_items item
                    JOIN places place ON place.id = item.place_id WHERE place.owner_user_id = ?)
                """)
                .param(id)
                .update();
        jdbc.sql("""
                UPDATE places SET owner_user_id = NULL, visibility = 'PRIVATE', status = 'DELETED',
                    source_place_id = NULL, name = '삭제된 장소', address = NULL,
                    road_address = NULL, latitude = 0, longitude = 0, phone = NULL,
                    homepage_url = NULL, image_url = NULL, basic_info = NULL,
                    operating_hours = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE owner_user_id = ?
                """)
                .param(id)
                .update();

        // 마지막으로 참여자 참조를 해제하고 일반 참여 기록을 삭제합니다.
        jdbc.sql("""
                UPDATE travel_supplies SET assigned_member_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE assigned_member_id IN (SELECT id FROM trip_participants WHERE user_id = ?)
                """)
                .param(id)
                .update();
        jdbc.sql("""
                DELETE FROM travel_routes
                WHERE member_id IN (SELECT id FROM trip_participants WHERE user_id = ?)
                """)
                .param(id)
                .update();
        jdbc.sql("""
                UPDATE trip_participants SET departure_place_id = NULL, return_place_id = NULL,
                    route_preferences = NULL, updated_at = CURRENT_TIMESTAMP WHERE user_id = ?
                """)
                .param(id)
                .update();
        jdbc.sql("DELETE FROM trip_participants WHERE user_id = ? AND role = 'MEMBER'")
        .param(id)
        .update();
    }

    /** 사용자 관련 사용자 업무를 처리합니다. */
    public boolean anonymize(long id) {
        return jdbc.sql("""
                UPDATE users SET nickname = '탈퇴한 사용자',
                    email = CONCAT('withdrawn-', id, '@invalid.local'), password_hash = NULL,
                    introduction = NULL, profile_image_url = NULL, status = 'WITHDRAW',
                    deleted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .param(id)
                .update() == 1;
    }

    private Optional<UserQueryResult> findBy(String column, Object value) {
        return jdbc.sql("SELECT " + USER_COLUMNS + " FROM users WHERE " + column + " = ? AND deleted_at IS NULL")
                .param(value)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapUser);
    }

    private UserQueryResult mapUser(Map<String, Object> row) {
        return new UserQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "nickname"),
                nullableText(row, "email"),
                nullableText(row, "introduction"),
                nullableText(row, "profile_image_url"),
                UserStatus.valueOf(RowSupport.strValue(row, "status")),
                nullableDateTime(row, "last_login_at"),
                dateTime(row, "created_at"),
                dateTime(row, "updated_at"));
    }

    private UserPersonalityQueryResult mapPersonality(Map<String, Object> row) {
        return new UserPersonalityQueryResult(
                nullableText(row, "result_code"),
                nullableText(row, "name"),
                nullableText(row, "character_key"),
                jsonList(row, "strengths"),
                jsonList(row, "weaknesses"));
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private LocalDateTime nullableDateTime(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }

    private LocalDateTime dateTime(Map<String, Object> row, String key) {
        return AppDateFormat.databaseDateTime(RowSupport.value(row, key));
    }

    @SuppressWarnings("unchecked")
    private List<String> jsonList(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null || value.toString().isBlank()
                ? List.of() : (List<String>) json.read(value.toString(), List.class);
    }
}
