package com.gayadi.server.place;

import com.gayadi.server.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class PlaceService {

    private final JdbcClient jdbc;

    public PlaceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String category) {
        if (category == null || category.isBlank()) {
            return jdbc.sql("SELECT * FROM places WHERE status = 'ACTIVE' ORDER BY name")
                    .query().listOfRows();
        }
        return jdbc.sql("SELECT * FROM places WHERE status = 'ACTIVE' AND category = ? ORDER BY name")
                .param(category.toUpperCase())
                .query().listOfRows();
    }

    public Map<String, Object> get(long id) {
        return jdbc.sql("SELECT * FROM places WHERE id = ? AND status != 'DELETED'")
                .param(id)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."));
    }
}
