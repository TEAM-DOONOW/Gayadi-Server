package com.gayadi.server.route.query;

/** 경로 계산에 필요한 참여자의 출발·귀가 장소를 전달합니다. */
public record RouteMemberQueryResult(
        long id,
        long userId,
        Long departurePlaceId,
        Long returnPlaceId
) {
}
