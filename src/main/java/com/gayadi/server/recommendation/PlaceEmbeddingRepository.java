package com.gayadi.server.recommendation;

import com.gayadi.server.common.RowSupport;
import com.gayadi.server.recommendation.query.PlaceEmbeddingQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 임베딩할 공개 장소를 ID 커서 기반으로 조회합니다. */
@Repository
public class PlaceEmbeddingRepository {

    private final JdbcClient jdbc;

    public PlaceEmbeddingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** 커서 이후 장소를 임베딩 갱신 배치 크기만큼 조회합니다. */
    public List<PlaceEmbeddingQueryResult> findBatchAfter(long cursor, int limit) {
        return jdbc.sql("""
                SELECT id, name, category, address, basic_info, latitude, longitude
                FROM places
                WHERE status = 'ACTIVE' AND visibility = 'PUBLIC' AND id > ?
                ORDER BY id
                LIMIT ?
                """)
                .params(cursor, limit)
                .query()
                .listOfRows()
                .stream()
                .map(this::map)
                .toList();
    }

    private PlaceEmbeddingQueryResult map(Map<String, Object> row) {
        return new PlaceEmbeddingQueryResult(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "name"),
                RowSupport.strValue(row, "category"),
                nullableText(row, "address"),
                nullableText(row, "basic_info"),
                ((Number) RowSupport.value(row, "latitude")).doubleValue(),
                ((Number) RowSupport.value(row, "longitude")).doubleValue());
    }

    private String nullableText(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase());
        }
        return value == null ? "" : value.toString();
    }
}
