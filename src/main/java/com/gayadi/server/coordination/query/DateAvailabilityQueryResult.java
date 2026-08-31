package com.gayadi.server.coordination.query;

import java.time.LocalDate;

/** 여행 날짜 조율 Repository의 DateAvailabilityQueryResult 조회 결과를 전달합니다. */
public record DateAvailabilityQueryResult(
        long userId,
        LocalDate date
) {
}
