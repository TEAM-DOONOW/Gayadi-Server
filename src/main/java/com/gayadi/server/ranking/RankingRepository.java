package com.gayadi.server.ranking;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 가야디 자체 데이터(찜)로 계산하는 순위 SQL을 담당합니다. */
@Repository
public class RankingRepository {

    private final JdbcClient jdbc;

    public RankingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 공개 맛집을 찜한 사용자 수 순으로 조회합니다.
     * 지역 토큰은 지역 이름이나 주소에 포함되면 일치로 봅니다(예: "강릉·속초" → 강릉, 속초).
     */
    public List<FavoriteRankRow> findRestaurantsByFavorites(List<String> regionTokens, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.address, p.road_address, p.image_url,
                       p.latitude, p.longitude, COUNT(f.user_id) AS favorite_count
                FROM places p
                JOIN regions r ON r.region_id = p.region_id
                JOIN user_favorite_places f ON f.place_id = p.id
                WHERE p.category = 'RESTAURANT' AND p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                """);
        List<Object> params = new ArrayList<>();
        if (!regionTokens.isEmpty()) {
            List<String> clauses = new ArrayList<>();
            for (String token : regionTokens) {
                clauses.add("r.name = ? OR COALESCE(p.address, '') LIKE ? ESCAPE '!'"
                        + " OR COALESCE(p.road_address, '') LIKE ? ESCAPE '!'");
                String pattern = "%" + likeLiteral(token) + "%";
                params.addAll(List.of(token, pattern, pattern));
            }
            sql.append(" AND (").append(String.join(" OR ", clauses)).append(")\n");
        }
        sql.append("""
                GROUP BY p.id, p.name, p.address, p.road_address, p.image_url, p.latitude, p.longitude
                ORDER BY favorite_count DESC, p.id DESC
                LIMIT ?
                """);
        params.add(limit);
        return jdbc.sql(sql.toString())
                .params(params)
                .query()
                .listOfRows()
                .stream()
                .map(RankingRepository::map)
                .toList();
    }

    private static FavoriteRankRow map(Map<String, Object> row) {
        String roadAddress = (String) value(row, "road_address");
        return new FavoriteRankRow(
                ((Number) value(row, "id")).longValue(),
                (String) value(row, "name"),
                roadAddress == null || roadAddress.isBlank() ? (String) value(row, "address") : roadAddress,
                (String) value(row, "image_url"),
                toDouble(value(row, "latitude")),
                toDouble(value(row, "longitude")),
                ((Number) value(row, "favorite_count")).longValue());
    }

    private static Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private static Double toDouble(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        return value instanceof Number number ? number.doubleValue() : null;
    }

    public record FavoriteRankRow(
            long placeId,
            String name,
            String address,
            String imageUrl,
            Double latitude,
            Double longitude,
            long favoriteCount
    ) {
    }

    /** 사용자 지역명의 %, _ 를 문자 그대로 비교합니다. */
    private static String likeLiteral(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
