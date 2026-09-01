package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.model.PlaceSearchPlan;
import com.gayadi.server.recommendation.model.TourContentType;
import com.gayadi.server.recommendation.model.TourPlaceCandidate;

import com.gayadi.server.tourapi.TourApiService;
import com.gayadi.server.tourapi.dto.request.AreaBasedListRequest;
import com.gayadi.server.tourapi.dto.request.KeywordSearchRequest;
import com.gayadi.server.tourapi.dto.request.LocationBasedListRequest;
import com.gayadi.server.tourapi.dto.response.TourListResponse;
import com.gayadi.server.tourapi.dto.response.TourPlaceDetailResponse;
import com.gayadi.server.tourapi.dto.response.TourPlaceResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

/** LLM의 검색 계획을 허용된 TourAPI 목록 호출로 변환합니다. */
@Component
public class TourApiPlaceSearchGateway implements TourPlaceSearchGateway {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_QUERIES = 6;
    private static final int MAX_CANDIDATES = 20;
    private static final int MAX_DETAIL_CANDIDATES = 5;
    private static final int MAX_DETAIL_SUMMARY_FIELDS = 8;
    private static final double EARTH_RADIUS_KM = 6_371.0;
    private static final Set<String> DETAIL_FIELDS = Set.of(
            "usetime", "usetimeculture", "usetimeleports", "usetimefood",
            "restdate", "restdateculture", "restdateleports", "restdatefood",
            "parking", "parkingculture", "parkingleports", "parkingfood",
            "usefee", "usefeeculture", "usefeeleports", "usefeefood");

    private final TourApiService tourApi;

    public TourApiPlaceSearchGateway(TourApiService tourApi) {
        this.tourApi = tourApi;
    }

    @Override
    public List<TourPlaceCandidate> search(PlaceSearchPlan plan, SearchContext context) {
        Map<String, TourPlaceCandidate> byId = new LinkedHashMap<>();
        if (plan == null || plan.queries() == null) {
            return List.of();
        }

        plan.queries().stream()
                .limit(MAX_QUERIES)
                .map(query -> query.withContext(
                        context.regionCode(), context.sigunguCode(),
                        context.latitude(), context.longitude()))
                .forEach(query -> execute(query, context, byId));

        List<TourPlaceCandidate> filtered = byId.values().stream()
                .filter(candidate -> !context.policy().indoorRequired()
                        || candidate.matchesIndoorRequirement())
                .limit(MAX_CANDIDATES)
                .toList();
        return IntStream.range(0, filtered.size())
                .mapToObj(index -> index < MAX_DETAIL_CANDIDATES
                        ? enrich(filtered.get(index)) : filtered.get(index))
                .toList();
    }

    private void execute(PlaceSearchPlan.Query query,
                         SearchContext context,
                         Map<String, TourPlaceCandidate> byId) {
        List<String> types = validTypes(query.contentTypeIds());
        if (types.isEmpty()) {
            types = List.of("");
        }

        switch (query.operation()) {
            case PlaceSearchPlan.OPERATION_AREA ->
                    types.forEach(type -> area(query, type, context, byId));
            case PlaceSearchPlan.OPERATION_LOCATION ->
                    types.forEach(type -> location(query, type, context, byId));
            case PlaceSearchPlan.OPERATION_KEYWORD -> {
                for (String keyword : query.keywords()) keyword(query, keyword, context, byId);
            }
            default -> {
                // 모델이 잘못된 operation을 만들면 외부 API를 호출하지 않는다.
            }
        }
    }

    private void area(PlaceSearchPlan.Query query, String type,
                      SearchContext context, Map<String, TourPlaceCandidate> byId) {
        paginate(query.maxPages(), cursor -> tourApi.areaBasedList(
                new AreaBasedListRequest(
                        PAGE_SIZE, cursor, "C", type,
                        query.regionCode(), query.sigunguCode(), null, null, null)),
                context, byId);
    }

    private void location(PlaceSearchPlan.Query query, String type,
                          SearchContext context, Map<String, TourPlaceCandidate> byId) {
        paginate(query.maxPages(), cursor -> tourApi.locationBasedList(
                new LocationBasedListRequest(
                        PAGE_SIZE, cursor, "E", query.mapX(), query.mapY(),
                        String.valueOf(query.radiusMeters()), type, null,
                        query.regionCode(), query.sigunguCode(), null, null, null)),
                context, byId);
    }

    private void keyword(PlaceSearchPlan.Query query, String keyword,
                         SearchContext context, Map<String, TourPlaceCandidate> byId) {
        paginate(query.maxPages(), cursor -> tourApi.searchKeyword(
                new KeywordSearchRequest(
                        PAGE_SIZE, cursor, "C", keyword,
                        query.regionCode(), query.sigunguCode(), null, null, null)),
                context, byId);
    }

    private void paginate(int maxPages,
                          PageCall call,
                          SearchContext context,
                          Map<String, TourPlaceCandidate> byId) {
        String cursor = null;
        for (int page = 0; page < maxPages; page++) {
            TourListResponse response = call.get(cursor);
            if (response == null || response.items() == null) {
                return;
            }
            response.items().stream()
                    .map(place -> toCandidate(place, context))
                    .filter(candidate -> !candidate.placeId().isBlank())
                    .forEach(candidate -> byId.putIfAbsent(candidate.placeId(), candidate));
            if (response.nextCursor() == null || response.nextCursor().isBlank()) {
                return;
            }
            cursor = response.nextCursor();
        }
    }

    private TourPlaceCandidate toCandidate(TourPlaceResponse place,
                                            SearchContext context) {
        Double latitude = decimal(place.mapY());
        Double longitude = decimal(place.mapX());
        Double distanceKm = decimal(place.dist());
        if (distanceKm != null) {
            distanceKm /= 1_000.0;
        }
        if (distanceKm == null && latitude != null && longitude != null) {
            distanceKm = distanceKm(latitude, longitude, context.latitude(), context.longitude());
        }
        return new TourPlaceCandidate(
                place.contentId(),
                place.title(),
                category(place.contentTypeId()),
                place.contentTypeId(),
                joinAddress(place.address(), place.addressDetail()),
                latitude,
                longitude,
                indoor(place.contentTypeId()),
                distanceKm,
                place.title() + " " + joinAddress(place.address(), place.addressDetail()),
                place.firstImage());
    }

    private TourPlaceCandidate enrich(TourPlaceCandidate candidate) {
        try {
            TourPlaceDetailResponse detail = tourApi.detail(
                    candidate.placeId(), candidate.contentTypeId());
            String overview = detail.common().getOrDefault("overview", "");
            String intro = detailSummary(detail.intro());
            String description = candidate.description();
            if (!overview.isBlank()) {
                description += "\n개요: " + overview;
            }
            if (!intro.isBlank()) {
                description += "\n운영 정보: " + intro;
            }
            return new TourPlaceCandidate(
                    candidate.placeId(), candidate.name(), candidate.category(),
                    candidate.contentTypeId(), candidate.address(), candidate.latitude(),
                    candidate.longitude(), candidate.indoor(), candidate.distanceKm(),
                    description, candidate.imageUrl());
        } catch (RuntimeException ignored) {
            // 목록 결과만으로도 추천을 계속할 수 있도록 상세 API 실패를 후보 전체 실패로 만들지 않는다.
            return candidate;
        }
    }

    private String detailSummary(Map<String, String> fields) {
        return fields.entrySet().stream()
                .filter(entry -> !entry.getValue().isBlank())
                .filter(entry -> DETAIL_FIELDS.contains(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .limit(MAX_DETAIL_SUMMARY_FIELDS)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    private List<String> validTypes(List<String> types) {
        return types == null ? List.of() : types.stream()
                .filter(TourContentType::supports)
                .distinct()
                .toList();
    }

    private String category(String contentTypeId) {
        TourContentType type = TourContentType.fromCode(contentTypeId);
        return type == null ? "ETC" : type.category();
    }

    private Boolean indoor(String contentTypeId) {
        TourContentType type = TourContentType.fromCode(contentTypeId);
        return type == null ? null : type.indoor();
    }

    private String joinAddress(String address, String detail) {
        if (detail == null || detail.isBlank()) {
            return address == null ? "" : address;
        }
        return (address == null ? "" : address) + " " + detail;
    }

    private Double decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private double distanceKm(double latitude, double longitude,
                              double originLatitude, double originLongitude) {
        double latitudeDistance = Math.toRadians(latitude - originLatitude);
        double longitudeDistance = Math.toRadians(longitude - originLongitude);
        double value = Math.sin(latitudeDistance / 2) * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(originLatitude)) * Math.cos(Math.toRadians(latitude))
                * Math.sin(longitudeDistance / 2) * Math.sin(longitudeDistance / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    @FunctionalInterface
    private interface PageCall {
        TourListResponse get(String cursor);
    }
}
