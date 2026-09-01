package com.gayadi.server.place;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.place.dto.response.PlacePageResponse;
import com.gayadi.server.place.dto.response.PlaceResponse;
import com.gayadi.server.place.model.PlaceCategory;
import com.gayadi.server.place.query.PlaceQueryResult;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/** 여행 장소 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class PlaceService {
    private static final int MAX_PAGE_SIZE = 50;
    private final PlaceRepository repository;

    public PlaceService(PlaceRepository repository) {
        this.repository = repository;
    }

    /** 검색어·지역·카테고리 조건으로 공개 장소 페이지를 조회합니다. */
    public PlacePageResponse list(String query, String region, String category, Long cursor, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_PAGE_SIZE));
        String normalizedQuery = normalizeText(query);
        String normalizedRegion = normalizeText(region);
        PlaceCategory normalizedCategory = normalizeCategory(category);
        if (normalizedQuery != null && normalizedQuery.length() > 100) {
            throw new BusinessException(PlaceErrorCode.PLACE_SEARCH_QUERY_TOO_LONG);
        }
        if (normalizedRegion != null && normalizedRegion.length() > 50) {
            throw new BusinessException(PlaceErrorCode.PLACE_REGION_TOO_LONG);
        }

        if (cursor != null) {
            if (cursor < 1) {
                throw new BusinessException(PlaceErrorCode.PLACE_CURSOR_INVALID);
            }
        }
        List<PlaceQueryResult> rows = repository.findAll(
                normalizedQuery, normalizedRegion, normalizedCategory, cursor, limit);
        boolean hasNext = rows.size() > limit;
        List<PlaceResponse> items = rows.stream().limit(limit).map(this::toResponse).toList();
        Long nextCursor = hasNext && !items.isEmpty() ? items.getLast().id() : null;
        return new PlacePageResponse(items, nextCursor, hasNext);
    }

    /** 장소 조건에 맞는 장소 정보를 조회합니다. */
    public PlaceResponse get(long id) {
        return repository.findPublic(id).map(this::toResponse)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private PlaceResponse toResponse(PlaceQueryResult place) {
        String label = categoryLabel(place.category());
        String basicInfo = place.basicInfo();
        String description = basicInfo != null && !basicInfo.isBlank() && !basicInfo.startsWith("{")
                ? basicInfo.trim() : place.regionName() + "의 " + label + " " + place.name();
        return new PlaceResponse(
                place.id(), place.name(), label, place.category(), 0.0, 0, 0, false,
                "NORMAL", false, categoryEmoji(place.category()), description,
                place.address(), place.roadAddress(), place.latitude(), place.longitude(),
                place.regionId(), place.regionName(), place.phone(), place.homepageUrl(),
                place.imageUrl(), place.indoor(), place.basicInfo(), place.operatingHours(), place.updatedAt());
    }

    private String categoryLabel(PlaceCategory category) {
        return switch (category) {
            case ATTRACTION -> "관광명소";
            case RESTAURANT -> "맛집";
            case ACCOMMODATION -> "숙소";
            case CAFE -> "카페";
            case SHELTER -> "실내 대피소";
            case CULTURE -> "문화";
            case SHOPPING -> "쇼핑";
            case ETC -> "기타";
        };
    }

    private String categoryEmoji(PlaceCategory category) {
        return switch (category) {
            case ATTRACTION -> "🏞️";
            case RESTAURANT -> "🍲";
            case ACCOMMODATION -> "🏨";
            case CAFE -> "☕";
            case SHELTER -> "🏠";
            case CULTURE -> "🎨";
            case SHOPPING -> "🛍️";
            case ETC -> "📍";
        };
    }

    private PlaceCategory normalizeCategory(String category) {
        String value = normalizeText(category);
        if (value == null) {
            return null;
        }
        try {
            return PlaceCategory.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(PlaceErrorCode.PLACE_CATEGORY_INVALID);
        }
    }

    private String normalizeText(String value) {

        return value == null || value.isBlank() ? null : value.trim();

    }
}
