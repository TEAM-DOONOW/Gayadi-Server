package com.gayadi.server.expense.query;

import java.time.LocalDate;

/** 여행 경비와 정산 Repository의 TripDateRangeQueryResult 조회 결과를 전달합니다. */
public record TripDateRangeQueryResult(
        LocalDate startDate,
        LocalDate endDate
) {
}
