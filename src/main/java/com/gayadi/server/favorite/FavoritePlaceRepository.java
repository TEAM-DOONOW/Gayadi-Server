package com.gayadi.server.favorite;

import com.gayadi.server.auth.UserErrorCode;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.favorite.query.FavoritePlaceQueryResult;
import com.gayadi.server.place.model.PlaceCategory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 사용자 찜 장소 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class FavoritePlaceRepository {

    private static final String SELECT_FAVORITE = """
            SELECT p.id, p.name, p.category, p.address, p.road_address,
                   p.latitude, p.longitude, p.region_id, p.phone,
                   p.homepage_url, p.image_url, p.indoor, p.basic_info,
                   f.memo, f.created_at AS favorited_at
            FROM user_favorite_places f
            JOIN places p ON p.id = f.place_id
            """;

    private final JdbcClient jdbc;

    public FavoritePlaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 전체 조건에 맞는 찜한 장소 데이터를 DB에서 조회합니다. */
    public List<FavoritePlaceQueryResult> findAll(long userId, int limit, int offset) {
        return jdbc.sql(SELECT_FAVORITE + """
                WHERE f.user_id = ? AND p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                ORDER BY f.created_at DESC, p.id DESC
                LIMIT ? OFFSET ?
                """)
                .params(userId, limit, offset)
                .query()
                .listOfRows()
                .stream()
                .map(this::map)
                .toList();
    }

    /** 찜한 장소 조건에 맞는 찜한 장소 데이터를 DB에서 조회합니다. */
    public Optional<FavoritePlaceQueryResult> find(long userId, long placeId) {
        return jdbc.sql(SELECT_FAVORITE + """
                WHERE f.user_id = ? AND f.place_id = ?
                  AND p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                """)
                .params(userId, placeId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::map);
    }

    /** 찜한 장소 찜한 장소 데이터를 DB에 저장합니다. */
    public void upsert(long userId, long placeId, String memo) {
        int updated = jdbc.sql("""
                UPDATE user_favorite_places SET memo = ?
                WHERE user_id = ? AND place_id = ?
                """)
                .params(memo, userId, placeId)
                .update();
        if (updated == 0) {
            jdbc.sql("""
                    INSERT INTO user_favorite_places (user_id, place_id, memo)
                    VALUES (?, ?, ?)
                    """)
                    .params(userId, placeId, memo)
                    .update();
        }
    }

    /** 찜한 장소 찜한 장소 데이터를 DB에서 삭제합니다. */
    public boolean delete(long userId, long placeId) {
        return jdbc.sql("DELETE FROM user_favorite_places WHERE user_id = ? AND place_id = ?")
                .params(userId, placeId)
                .update() > 0;
    }

    /** 변경 충돌을 막기 위해 사용자 DB 행을 잠급니다. */
    public void lockUser(long userId) {
        jdbc.sql("SELECT id FROM users WHERE id = ? AND deleted_at IS NULL FOR UPDATE")
                .param(userId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private FavoritePlaceQueryResult map(Map<String, Object> row) {
        return new FavoritePlaceQueryResult(
                number(row, "id").longValue(),
                text(row, "name"),
                PlaceCategory.valueOf(text(row, "category")),
                textOrNull(row, "address"),
                textOrNull(row, "road_address"),
                doubleOrNull(row, "latitude"),
                doubleOrNull(row, "longitude"),
                longOrNull(row, "region_id"),
                textOrNull(row, "phone"),
                textOrNull(row, "homepage_url"),
                textOrNull(row, "image_url"),
                booleanOrNull(row, "indoor"),
                textOrNull(row, "basic_info"),
                textOrNull(row, "memo"),
                dateTimeOrNull(row, "favorited_at"));
    }

    private Object raw(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private Number number(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value instanceof Number number ? number : Long.parseLong(value.toString());
    }

    private String text(Map<String, Object> row, String key) {
        return raw(row, key).toString();
    }

    private String textOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value.toString();
    }

    private Long longOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : (value instanceof Number number
                ? number.longValue() : Long.parseLong(value.toString()));
    }

    private Double doubleOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : (value instanceof Number number
                ? number.doubleValue() : Double.parseDouble(value.toString()));
    }

    private Boolean booleanOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : (value instanceof Boolean bool
                ? bool : Boolean.valueOf(value.toString()));
    }

    private LocalDateTime dateTimeOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }
}
