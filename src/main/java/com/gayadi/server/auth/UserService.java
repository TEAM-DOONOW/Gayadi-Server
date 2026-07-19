package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
public class UserService {
    private final JdbcClient jdbc;

    public UserService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> create(String nickname) {
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO users(id, nickname, oauth_provider, oauth_subject) VALUES (?, ?, 'LOCAL', ?)")
                .params(id, nickname, "local:" + id)
                .update();
        return get(id);
    }

    public Map<String, Object> get(String id) {
        return jdbc.sql("SELECT id, nickname, oauth_provider, status, created_at FROM users WHERE id = ?")
                .param(id).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    public void requireExists(String id) {
        if (jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ?").param(id).query(Integer.class).single() == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }
}
