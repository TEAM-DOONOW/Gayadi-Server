package com.gayadi.server.recommendation;

import java.util.List;

/** LLM이 생성하고 서버가 검증한 TourAPI 검색 계획입니다. */
public record PlaceSearchPlan(
        List<Query> queries,
        int maxSearchRounds
) {

    static final String OPERATION_AREA = "AREA";
    static final String OPERATION_LOCATION = "LOCATION";
    static final String OPERATION_KEYWORD = "KEYWORD";
    private static final int MAX_SEARCH_ROUNDS = 2;
    private static final int MAX_KEYWORDS = 5;
    private static final int DEFAULT_RADIUS_METERS = 15_000;
    private static final int MIN_RADIUS_METERS = 100;
    private static final int MAX_RADIUS_METERS = 20_000;
    private static final int MAX_PAGES = 3;

    public PlaceSearchPlan {
        queries = queries == null ? List.of() : List.copyOf(queries);
        maxSearchRounds = Math.max(1, Math.min(maxSearchRounds, MAX_SEARCH_ROUNDS));
    }

    public static PlaceSearchPlan fallback(String destination, String regionCode, String sigunguCode,
                                           List<String> keywords, TravelSituation.Policy policy) {
        List<String> safeKeywords = keywords == null ? List.of() : keywords.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .limit(MAX_KEYWORDS)
                .toList();
        List<String> types = policy.indoorRequired()
                ? TourContentType.indoorRecommendationCodes()
                : TourContentType.generalRecommendationCodes();
        Query keywordQuery = new Query(
                safeKeywords.isEmpty()
                        ? List.of(destination == null || destination.isBlank() ? "여행지" : destination.trim())
                        : safeKeywords,
                OPERATION_KEYWORD, types, regionCode, sigunguCode,
                null, null, DEFAULT_RADIUS_METERS, 1);
        Query areaQuery = new Query(
                List.of(), OPERATION_AREA, types, regionCode, sigunguCode,
                null, null, DEFAULT_RADIUS_METERS, 1);
        return new PlaceSearchPlan(List.of(keywordQuery, areaQuery), 1);
    }

    public record Query(
            List<String> keywords,
            String operation,
            List<String> contentTypeIds,
            String regionCode,
            String sigunguCode,
            String mapX,
            String mapY,
            Integer radiusMeters,
            Integer maxPages
    ) {

        public Query {
            keywords = keywords == null ? List.of() : keywords.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .limit(MAX_KEYWORDS)
                    .toList();
            operation = operation == null || operation.isBlank()
                    ? OPERATION_KEYWORD : operation.trim().toUpperCase();
            contentTypeIds = contentTypeIds == null ? List.of() : contentTypeIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
            radiusMeters = radiusMeters == null
                    ? DEFAULT_RADIUS_METERS
                    : Math.max(MIN_RADIUS_METERS, Math.min(radiusMeters, MAX_RADIUS_METERS));
            maxPages = maxPages == null ? 1 : Math.max(1, Math.min(maxPages, MAX_PAGES));
        }

        public Query withContext(String defaultRegionCode, String defaultSigunguCode,
                                 double latitude, double longitude) {
            return new Query(
                    keywords,
                    operation,
                    contentTypeIds,
                    defaultRegionCode,
                    defaultSigunguCode,
                    String.valueOf(longitude),
                    String.valueOf(latitude),
                    radiusMeters,
                    maxPages);
        }

    }
}
