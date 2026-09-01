package com.gayadi.server.route.query;

/** 선택 가능한 추천 경로의 식별자와 저장 데이터를 전달합니다. */
public record RouteOptionQueryResult(
        long id,
        String routeData
) {
}
