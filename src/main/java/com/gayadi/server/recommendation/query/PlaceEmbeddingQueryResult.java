package com.gayadi.server.recommendation.query;

/** 벡터 검색 문서 생성에 필요한 장소 조회 결과입니다. */
public record PlaceEmbeddingQueryResult(
        long id,
        String name,
        String category,
        String address,
        String basicInfo,
        double latitude,
        double longitude
) {
}
