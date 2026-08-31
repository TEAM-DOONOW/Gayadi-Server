package com.gayadi.server.place;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.place.model.PlaceCategory;
import com.gayadi.server.place.query.PlaceQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;

/** 여행 장소 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class PlaceRepository {

    private static final String SELECT_FIELDS = """
            SELECT p.id, p.name, p.category, p.address, p.road_address,
                   p.latitude, p.longitude, p.region_id, r.name AS region_name,
                   p.phone, p.homepage_url, p.image_url, p.indoor,
                   p.basic_info, p.operating_hours, p.updated_at
            FROM places p
            JOIN regions r ON r.region_id = p.region_id
            WHERE p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
            """;

    private final JdbcClient jdbc;

    public PlaceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 전체 조건에 맞는 장소 데이터를 DB에서 조회합니다. */
    public List<PlaceQueryResult> findAll(
            String query, String region, PlaceCategory category, Long cursor, int limit) {
        StringBuilder condition = new StringBuilder();
        List<Object> parameters = new ArrayList<>();
        if (query != null) {
            condition.append("""
                     AND (LOWER(p.name) LIKE ?
                          OR LOWER(COALESCE(p.address, '')) LIKE ?
                          OR LOWER(COALESCE(p.road_address, '')) LIKE ?
                          OR LOWER(COALESCE(p.basic_info, '')) LIKE ?)
                    """);
            String pattern = "%" + query.toLowerCase(Locale.ROOT) + "%";
            parameters.addAll(List.of(pattern, pattern, pattern, pattern));
        }
        if (region != null) {
            Long regionId = parsePositiveLong(region);
            condition.append(regionId == null ? " AND r.name = ?\n" : " AND p.region_id = ?\n");
            parameters.add(regionId == null ? region : regionId);
        }
        if (category != null) {
            condition.append(" AND p.category = ?\n");
            parameters.add(category.name());
        }
        if (cursor != null) {
            condition.append(" AND p.id < ?\n");
            parameters.add(cursor);
        }
        condition.append(" ORDER BY p.id DESC LIMIT ?");
        parameters.add(limit + 1);
        return jdbc.sql(SELECT_FIELDS + condition)
                .params(parameters)
                .query()
                .listOfRows()
                .stream()
                .map(this::map)
                .toList();
    }

    private Long parsePositiveLong(String value) {
        try {
            long number = Long.parseLong(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** 공개 조건에 맞는 장소 데이터를 DB에서 조회합니다. */
    public Optional<PlaceQueryResult> findPublic(long id) {
        return jdbc.sql(SELECT_FIELDS + " AND p.id = ?")
                .param(id)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::map);
    }

    private PlaceQueryResult map(Map<String, Object> row) {
        return new PlaceQueryResult(
                number(row, "id").longValue(),
                text(row, "name"),
                PlaceCategory.valueOf(text(row, "category")),
                textOrNull(row, "address"),
                textOrNull(row, "road_address"),
                doubleOrNull(row, "latitude"),
                doubleOrNull(row, "longitude"),
                number(row, "region_id").longValue(),
                text(row, "region_name"),
                textOrNull(row, "phone"),
                textOrNull(row, "homepage_url"),
                textOrNull(row, "image_url"),
                booleanOrNull(row, "indoor"),
                textOrNull(row, "basic_info"),
                textOrNull(row, "operating_hours"),
                dateTimeOrNull(row, "updated_at"));
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

    private Double doubleOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value instanceof Number number
                ? number.doubleValue() : Double.valueOf(value.toString());
    }

    private Boolean booleanOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : value instanceof Boolean bool
                ? bool : Boolean.valueOf(value.toString());
    }

    private java.time.LocalDateTime dateTimeOrNull(Map<String, Object> row, String key) {
        Object value = raw(row, key);
        return value == null ? null : AppDateFormat.databaseDateTime(value);
    }
}
