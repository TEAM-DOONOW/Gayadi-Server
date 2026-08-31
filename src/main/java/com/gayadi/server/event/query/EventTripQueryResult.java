package com.gayadi.server.event.query;

/** 현장 상황 처리에 필요한 여행 지역과 진행 상태 조회 결과입니다. */
public record EventTripQueryResult(
        long regionId,
        String status
) {
}
