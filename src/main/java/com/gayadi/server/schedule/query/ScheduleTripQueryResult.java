package com.gayadi.server.schedule.query;

import com.gayadi.server.travel.model.TripStatus;
import java.time.LocalDate;

/** 여행 일정과 계획 Repository의 ScheduleTripQueryResult 조회 결과를 전달합니다. */
public record ScheduleTripQueryResult(
        long id,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status
) {
}
