package com.gayadi.server.schedule.query;

import com.gayadi.server.travel.model.TripStatus;
import java.time.LocalDate;

/** 여행 일정과 계획 Repository의 PlanTripQueryResult 조회 결과를 전달합니다. */
public record PlanTripQueryResult(
        long id,
        long ownerId,
        long regionId,
        LocalDate startDate,
        LocalDate endDate,
        TripStatus status
) {
}
