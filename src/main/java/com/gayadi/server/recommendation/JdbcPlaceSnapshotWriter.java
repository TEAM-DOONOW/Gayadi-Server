package com.gayadi.server.recommendation;

import com.gayadi.server.common.KeyHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** TourAPI 후보를 일정에서 재사용할 수 있는 공개 장소 스냅샷으로 저장합니다. */
@Service
public class JdbcPlaceSnapshotWriter implements PlaceSnapshotWriter {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public JdbcPlaceSnapshotWriter(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    @Override
    @Transactional
    public Map<String, Long> save(List<TourPlaceCandidate> candidates, String destination) {
        if (destination == null || destination.isBlank() || candidates == null || candidates.isEmpty()) {
            return Map.of();
        }
        List<TourPlaceCandidate> storable = candidates.stream()
                .filter(candidate -> candidate != null
                        && !candidate.placeId().isBlank()
                        && candidate.latitude() != null
                        && candidate.longitude() != null)
                .toList();
        if (storable.isEmpty()) return Map.of();

        long regionId = regionId(destination, storable.getFirst());
        Map<String, Long> result = new LinkedHashMap<>();
        for (TourPlaceCandidate candidate : storable) {
            long id = updateExisting(candidate, regionId);
            if (id == 0) {
                id = keyHelper.insert("""
                        INSERT INTO places (source, visibility, source_place_id, name, category,
                                            address, latitude, longitude, region_id, basic_info,
                                            indoor, status)
                        VALUES ('TOUR_API', 'PUBLIC', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')
                        """,
                        candidate.placeId(), candidate.name(), candidate.category(), candidate.address(),
                        candidate.latitude(), candidate.longitude(), regionId,
                        candidate.description(), candidate.indoor());
            }
            result.put(candidate.placeId(), id);
        }
        return Map.copyOf(result);
    }

    private long regionId(String destination, TourPlaceCandidate candidate) {
        Long existing = jdbc.sql("SELECT region_id FROM regions WHERE name = ?")
                .param(destination.trim())
                .query(Long.class)
                .optional()
                .orElse(null);
        if (existing != null) return existing;

        try {
            jdbc.sql("INSERT INTO regions (name, latitude, longitude) VALUES (?, ?, ?)")
                    .params(destination.trim(), candidate.latitude(), candidate.longitude())
                    .update();
        } catch (DuplicateKeyException ignored) {
            // 동시에 같은 목적지를 처음 저장한 요청의 지역을 재사용한다.
        }
        return jdbc.sql("SELECT region_id FROM regions WHERE name = ?")
                .param(destination.trim())
                .query(Long.class)
                .single();
    }

    private long updateExisting(TourPlaceCandidate candidate, long regionId) {
        List<Long> ids = jdbc.sql("""
                SELECT id FROM places
                WHERE source = 'TOUR_API' AND source_place_id = ?
                """)
                .param(candidate.placeId())
                .query(Long.class)
                .list();
        if (ids.isEmpty()) return 0;

        jdbc.sql("""
                UPDATE places
                SET region_id = ?, name = ?, category = ?, address = ?,
                    latitude = ?, longitude = ?, basic_info = ?, indoor = ?,
                    status = 'ACTIVE', visibility = 'PUBLIC', updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """)
                .params(regionId, candidate.name(), candidate.category(), candidate.address(),
                        candidate.latitude(), candidate.longitude(), candidate.description(),
                        candidate.indoor(), ids.getFirst())
                .update();
        return ids.getFirst();
    }
}
