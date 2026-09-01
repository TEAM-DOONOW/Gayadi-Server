package com.gayadi.server.auth;

import com.gayadi.server.auth.model.UserStatus;
import com.gayadi.server.auth.query.LoginAccountQueryResult;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/** 인증과 사용자 계정 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class AuthRepository {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public AuthRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 닉네임·이메일·암호화된 비밀번호로 새 사용자 계정을 저장합니다. */
    public long createAccount(String nickname, String email, String passwordHash) {
        return keyHelper.insert(
                "INSERT INTO users (nickname, email, password_hash) VALUES (?, ?, ?)",
                nickname, email, passwordHash);
    }

    /** 이메일 정보를 DB에서 조회합니다. */
    public Optional<LoginAccountQueryResult> findByEmailForUpdate(String email) {
        return jdbc.sql("""
                SELECT id, email, status, password_hash, failed_login_attempts, login_locked_until
                FROM users
                WHERE email = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(email)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapLoginAccount);
    }

    /** 실패 로그인 상태를 만료하거나 해제합니다. */
    public void clearFailedLogins(long userId) {
        jdbc.sql("""
                UPDATE users
                SET last_login_at = CURRENT_TIMESTAMP, failed_login_attempts = 0,
                    login_locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .param(userId)
                .update();
    }

    /** 실패 로그인 정보를 DB에 저장합니다. */
    public void recordFailedLogin(
            long userId,
            int maxFailedAttempts,
            LocalDateTime lockedUntil) {
        jdbc.sql("""
                UPDATE users
                SET failed_login_attempts = CASE
                        WHEN failed_login_attempts + 1 >= ? THEN 0
                        ELSE failed_login_attempts + 1
                    END,
                    login_locked_until = CASE
                        WHEN failed_login_attempts + 1 >= ? THEN ?
                        ELSE login_locked_until
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                """)
                .params(maxFailedAttempts, maxFailedAttempts, lockedUntil, userId)
                .update();
    }

    private LoginAccountQueryResult mapLoginAccount(Map<String, Object> row) {
        Object lockedUntil = nullable(row, "login_locked_until");
        return new LoginAccountQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "email"),
                UserStatus.valueOf(RowSupport.strValue(row, "status")),
                nullableText(RowSupport.value(row, "password_hash")),
                ((Number) RowSupport.value(row, "failed_login_attempts")).intValue(),
                lockedUntil == null ? null : AppDateFormat.databaseDateTime(lockedUntil));
    }

    private String nullableText(Object value) {
        return value == null ? null : value.toString();
    }

    private Object nullable(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase());
    }
}
