package com.gayadi.server.favorite;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.ApiException;
import com.gayadi.server.place.PlaceService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class FavoritePlaceService {

    private final JdbcClient jdbc;
    private final UserService users;
    private final PlaceService places;

    public FavoritePlaceService(JdbcClient jdbc, UserService users, PlaceService places) {
        this.jdbc = jdbc;
        this.users = users;
        this.places = places;
    }

    public List<Map<String, Object>> list(long userId) {
        return list(userId, 100, 0);
    }

    public List<Map<String, Object>> list(long userId, int requestedLimit, int requestedOffset) {
        users.requireExists(userId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        int offset = Math.max(0, requestedOffset);
        return jdbc.sql("""
                SELECT p.id, p.name, p.category, p.address, p.road_address,
                       p.latitude, p.longitude, p.region_id, p.phone,
                       p.homepage_url, p.image_url, p.indoor, p.basic_info,
                       f.memo, f.created_at AS favorited_at
                FROM user_favorite_places f
                JOIN places p ON p.id = f.place_id
                WHERE f.user_id = ? AND p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                ORDER BY f.created_at DESC, p.id DESC
                LIMIT ? OFFSET ?
                """)
                .params(userId, limit, offset)
                .query().listOfRows().stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public Map<String, Object> save(long userId, long placeId, String memo) {
        users.requireExists(userId);
        lockUser(userId);
        places.get(placeId);

        int updated = jdbc.sql("""
                UPDATE user_favorite_places SET memo = ?
                WHERE user_id = ? AND place_id = ?
                """)
                .params(normalizeMemo(memo), userId, placeId)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO user_favorite_places (user_id, place_id, memo)
                    VALUES (?, ?, ?)
                    """)
                    .params(userId, placeId, normalizeMemo(memo))
                    .update();
        }
        return favorite(userId, placeId);
    }

    @Transactional
    public void delete(long userId, long placeId) {
        users.requireExists(userId);
        lockUser(userId);
        int deleted = jdbc.sql("""
                DELETE FROM user_favorite_places WHERE user_id = ? AND place_id = ?
                """)
                .params(userId, placeId)
                .update();
        if (deleted == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "찜한 장소를 찾을 수 없습니다.");
        }
    }

    private void lockUser(long userId) {
        jdbc.sql("SELECT id FROM users WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(userId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        return memo.trim();
    }

    private Map<String, Object> favorite(long userId, long placeId) {
        Map<String, Object> row = jdbc.sql("""
                SELECT p.id, p.name, p.category, p.address, p.road_address,
                       p.latitude, p.longitude, p.region_id, p.phone,
                       p.homepage_url, p.image_url, p.indoor, p.basic_info,
                       f.memo, f.created_at AS favorited_at
                FROM user_favorite_places f
                JOIN places p ON p.id = f.place_id
                WHERE f.user_id = ? AND f.place_id = ?
                  AND p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                """)
                .params(userId, placeId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "장소 찜을 저장하지 못했습니다."));
        return toView(row);
    }

    private Map<String, Object> toView(Map<String, Object> row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", number(row, "id").longValue());
        value.put("name", text(row, "name"));
        value.put("category", text(row, "category"));
        put(value, "address", row, "address");
        put(value, "roadAddress", row, "road_address");
        put(value, "latitude", row, "latitude");
        put(value, "longitude", row, "longitude");
        put(value, "regionId", row, "region_id");
        put(value, "phone", row, "phone");
        put(value, "homepageUrl", row, "homepage_url");
        put(value, "imageUrl", row, "image_url");
        put(value, "indoor", row, "indoor");
        put(value, "description", row, "basic_info");
        put(value, "memo", row, "memo");
        put(value, "favoritedAt", row, "favorited_at");
        return value;
    }

    private void put(Map<String, Object> target, String key, Map<String, Object> row, String rowKey) {
        Object value = raw(row, rowKey);
        if (value != null) target.put(key, value);
    }

    private String text(Map<String, Object> row, String key) {
        return raw(row, key).toString();
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value instanceof Number number ? number : Long.parseLong(value.toString());
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }
}
