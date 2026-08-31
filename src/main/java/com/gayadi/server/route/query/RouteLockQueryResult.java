package com.gayadi.server.route.query;

import com.gayadi.server.route.RoutePhase;

/** 선택할 경로의 잠금 상태와 소유 참여자를 전달합니다. */
public record RouteLockQueryResult(
        long id,
        long planId,
        Long memberId,
        RoutePhase phase,
        String status
) {
}
