package com.gayadi.server.event.query;

/** 일정 변경 제안의 대상 일정 ID와 버전 조회 결과입니다. */
public record EventPlanQueryResult(
        long id,
        int version
) {
}
