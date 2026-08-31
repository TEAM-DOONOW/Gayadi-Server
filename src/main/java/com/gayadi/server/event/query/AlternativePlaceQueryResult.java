package com.gayadi.server.event.query;

/** 일정 변경 후보로 사용할 수 있는 장소의 최소 조회 결과입니다. */
public record AlternativePlaceQueryResult(
        long id,
        String name
) {
}
