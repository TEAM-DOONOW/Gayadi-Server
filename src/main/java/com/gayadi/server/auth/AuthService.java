package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.KeyHelper;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Locale;

@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserService userService;
    private final JwtService jwtService;
    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserService userService, JwtService jwtService, JdbcClient jdbc, KeyHelper keyHelper) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public Map<String, Object> signup(String email, String password, String nickname) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            long id = keyHelper.insert(
                    "INSERT INTO users (nickname, email, password_hash) VALUES (?, ?, ?)",
                    nickname.trim(), normalizedEmail, passwordEncoder.encode(password));
            return loginResult(id);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
            }
            throw exception;
        }
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null
                ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("uk_users_email");
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        Map<String, Object> user = jdbc.sql("""
                SELECT id, email, status, password_hash, failed_login_attempts, login_locked_until
                FROM users
                WHERE email = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(normalizedEmail)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> unauthorized());
        long userId = ((Number) user.get("id")).longValue();
        if (!"ACTIVE".equals(String.valueOf(user.get("status")))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }

        LocalDateTime lockedUntil = localDateTime(user.get("login_locked_until"));
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 많아 잠시 잠겼습니다. 15분 뒤 다시 시도해 주세요.");
        }

        String hash = user.get("password_hash") == null ? null : user.get("password_hash").toString();
        if (hash == null || !passwordEncoder.matches(password, hash)) {
            recordFailedLogin(userId);
            throw unauthorized();
        }

        jdbc.sql("""
                UPDATE users
                SET last_login_at = CURRENT_TIMESTAMP, failed_login_attempts = 0,
                    login_locked_until = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .param(userId)
                .update();
        return loginResult(userId);
    }

    private void recordFailedLogin(long userId) {
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
                .params(MAX_FAILED_ATTEMPTS, MAX_FAILED_ATTEMPTS,
                        LocalDateTime.now().plusMinutes(LOCK_MINUTES), userId)
                .update();
    }

    private LocalDateTime localDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        return LocalDateTime.parse(value.toString().replace(' ', 'T'));
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private Map<String, Object> loginResult(long userId) {
        Map<String, Object> user = userService.get(userId);
        String email = user.get("email") == null ? "" : user.get("email").toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", jwtService.issue(userId, email));
        result.put("tokenType", "Bearer");
        result.put("expiresIn", jwtService.getExpiresIn().toSeconds());
        result.put("user", user);
        return result;
    }
}
