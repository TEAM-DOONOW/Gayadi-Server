package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.KeyHelper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class UserService {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public UserService(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    public Map<String, Object> create(String nickname) {
        long id = keyHelper.insert("INSERT INTO users (nickname) VALUES (?)", nickname);
        return get(id);
    }

    public Map<String, Object> get(long id) {
        return jdbc.sql("""
                SELECT id, nickname, introduction, profile_image_url, status,
                       last_login_at, created_at, updated_at
                FROM users WHERE id = ? AND deleted_at IS NULL
                """)
                .param(id)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    public void requireExists(long id) {
        long count = jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ? AND deleted_at IS NULL")
                .param(id)
                .query(Long.class)
                .optional()
                .orElse(0L);
        if (count == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }
}
