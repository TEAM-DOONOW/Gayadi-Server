package com.gayadi.server.schedule.query;

import com.gayadi.server.schedule.model.ScheduleType;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 여행 일정과 계획 Repository의 EditableScheduleItemQueryResult 조회 결과를 전달합니다. */
public record EditableScheduleItemQueryResult(
        long id,
        long planId,
        String title,
        Long placeId,
        LocalDate planDate,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        String memo,
        ScheduleType type,
        int sequenceNo,
        boolean visited
) {
}
