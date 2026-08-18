package com.gayadi.server.place;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.RowSupport;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PlaceService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Set<String> CATEGORIES = Set.of(
            "ATTRACTION", "RESTAURANT", "ACCOMMODATION", "CAFE",
            "SHELTER", "CULTURE", "SHOPPING", "ETC");

    private final JdbcClient jdbc;

    public PlaceService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PlacePage list(String query, String region, String category, Long cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        String normalizedQuery = normalizeText(query);
        String normalizedRegion = normalizeText(region);
        String normalizedCategory = normalizeCategory(category);
        if (normalizedQuery != null && normalizedQuery.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "장소 검색어는 100자까지 입력할 수 있습니다.");
        }
        if (normalizedRegion != null && normalizedRegion.length() > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "지역 이름은 50자까지 입력할 수 있습니다.");
        }

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.category, p.address, p.road_address,
                       p.latitude, p.longitude, p.region_id, r.name AS region_name,
                       p.image_url, p.indoor, p.basic_info
                FROM places p
                JOIN regions r ON r.region_id = p.region_id
                WHERE p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                """);
        List<Object> parameters = new ArrayList<>();

        if (normalizedQuery != null) {
            sql.append("""
                     AND (LOWER(p.name) LIKE ?
                          OR LOWER(COALESCE(p.address, '')) LIKE ?
                          OR LOWER(COALESCE(p.road_address, '')) LIKE ?
                          OR LOWER(COALESCE(p.basic_info, '')) LIKE ?)
                    """);
            String pattern = "%" + normalizedQuery.toLowerCase(Locale.ROOT) + "%";
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
            parameters.add(pattern);
        }
        if (normalizedRegion != null) {
            Long regionId = parsePositiveLong(normalizedRegion);
            if (regionId == null) {
                sql.append(" AND r.name = ?\n");
                parameters.add(normalizedRegion);
            } else {
                sql.append(" AND p.region_id = ?\n");
                parameters.add(regionId);
            }
        }
        if (normalizedCategory != null) {
            sql.append(" AND p.category = ?\n");
            parameters.add(normalizedCategory);
        }
        if (cursor != null) {
            if (cursor < 1) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "장소 목록 기준값은 1 이상이어야 합니다.");
            }
            sql.append(" AND p.id < ?\n");
            parameters.add(cursor);
        }
        sql.append(" ORDER BY p.id DESC LIMIT ?");
        parameters.add(limit + 1);

        List<Map<String, Object>> rows = jdbc.sql(sql.toString())
                .params(parameters)
                .query().listOfRows();
        boolean hasNext = rows.size() > limit;
        List<Map<String, Object>> items = rows.stream()
                .limit(limit)
                .map(this::toSummary)
                .toList();
        Long nextCursor = hasNext && !items.isEmpty()
                ? RowSupport.longValue(items.getLast(), "id")
                : null;
        return new PlacePage(items, nextCursor, hasNext);
    }

    /** 공개 API와 장소 찜에서 함께 사용하는 공개 장소 조회입니다. */
    public Map<String, Object> get(long id) {
        Map<String, Object> row = jdbc.sql("""
                SELECT p.id, p.name, p.category, p.address, p.road_address,
                       p.latitude, p.longitude, p.region_id, r.name AS region_name,
                       p.phone, p.homepage_url, p.image_url, p.indoor,
                       p.basic_info, p.operating_hours, p.updated_at
                FROM places p
                JOIN regions r ON r.region_id = p.region_id
                WHERE p.id = ? AND p.status = 'ACTIVE' AND p.visibility = 'PUBLIC'
                """)
                .param(id)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "공개된 장소를 찾을 수 없습니다."));
        return toDetail(row);
    }

    private Map<String, Object> toSummary(Map<String, Object> row) {
        Map<String, Object> result = commonFields(row);
        putIfPresent(result, "imageUrl", row, "image_url");
        putIfPresent(result, "indoor", row, "indoor");
        putIfPresent(result, "basicInfo", row, "basic_info");
        return result;
    }

    private Map<String, Object> toDetail(Map<String, Object> row) {
        Map<String, Object> result = commonFields(row);
        putIfPresent(result, "phone", row, "phone");
        putIfPresent(result, "homepageUrl", row, "homepage_url");
        putIfPresent(result, "imageUrl", row, "image_url");
        putIfPresent(result, "indoor", row, "indoor");
        putIfPresent(result, "basicInfo", row, "basic_info");
        putIfPresent(result, "operatingHours", row, "operating_hours");
        putIfPresent(result, "updatedAt", row, "updated_at");
        return result;
    }

    private Map<String, Object> commonFields(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", RowSupport.longValue(row, "id"));
        result.put("name", RowSupport.strValue(row, "name"));
        String categoryCode = RowSupport.strValue(row, "category");
        result.put("category", categoryLabel(categoryCode));
        result.put("categoryCode", categoryCode);
        result.put("rating", 0.0);
        result.put("reviews", 0);
        result.put("reviewCount", 0);
        result.put("ratingAvailable", false);
        result.put("crowdLevel", "NORMAL");
        result.put("crowdDataAvailable", false);
        result.put("emoji", categoryEmoji(categoryCode));
        result.put("description", description(row, categoryCode));
        putIfPresent(result, "address", row, "address");
        putIfPresent(result, "roadAddress", row, "road_address");
        result.put("latitude", RowSupport.value(row, "latitude"));
        result.put("longitude", RowSupport.value(row, "longitude"));
        result.put("regionId", RowSupport.longValue(row, "region_id"));
        result.put("regionName", RowSupport.strValue(row, "region_name"));
        return result;
    }

    private String categoryLabel(String category) {
        return switch (category) {
            case "ATTRACTION" -> "관광명소";
            case "RESTAURANT" -> "맛집";
            case "ACCOMMODATION" -> "숙소";
            case "CAFE" -> "카페";
            case "SHELTER" -> "실내 대피소";
            case "CULTURE" -> "문화";
            case "SHOPPING" -> "쇼핑";
            default -> "기타";
        };
    }

    private String categoryEmoji(String category) {
        return switch (category) {
            case "ATTRACTION" -> "🏞️";
            case "RESTAURANT" -> "🍲";
            case "ACCOMMODATION" -> "🏨";
            case "CAFE" -> "☕";
            case "SHELTER" -> "🏠";
            case "CULTURE" -> "🎨";
            case "SHOPPING" -> "🛍️";
            default -> "📍";
        };
    }

    private String description(Map<String, Object> row, String categoryCode) {
        Object basicInfo = nullableValue(row, "basic_info");
        if (basicInfo != null) {
            String text = basicInfo.toString().trim();
            if (!text.isBlank() && !text.startsWith("{")) return text;
        }
        return RowSupport.strValue(row, "region_name") + "의 "
                + categoryLabel(categoryCode) + " " + RowSupport.strValue(row, "name");
    }

    private void putIfPresent(Map<String, Object> target, String targetKey,
                              Map<String, Object> row, String rowKey) {
        Object value = nullableValue(row, rowKey);
        if (value != null) target.put(targetKey, value);
    }

    private Object nullableValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String normalizeCategory(String category) {
        String value = normalizeText(category);
        if (value == null) return null;
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "올바르지 않은 장소 분류입니다.");
        }
        return normalized;
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private Long parsePositiveLong(String value) {
        try {
            long number = Long.parseLong(value);
            return number > 0 ? number : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record PlacePage(List<Map<String, Object>> items, Long nextCursor, boolean hasNext) {
    }
}
