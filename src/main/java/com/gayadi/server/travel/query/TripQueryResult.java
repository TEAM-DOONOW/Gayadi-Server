package com.gayadi.server.travel.query;

import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.model.TripStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 여행과 참여자 Repository의 TripQueryResult 조회 결과를 전달합니다. */
public record TripQueryResult(
        long id,
        long ownerId,
        String title,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        DepartureMode departureMode,
        LocalDateTime meetingAt,
        Long meetingPlaceId,
        long regionId,
        String tripPreferences,
        TripStatus status,
        Integer maxMembers,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        int version,
        String inviteCode
) {
}
