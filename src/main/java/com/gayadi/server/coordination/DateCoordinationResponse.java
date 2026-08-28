package com.gayadi.server.coordination;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "DateCoordinationResponse", description = "그룹 여행 날짜 조율 현황")
public record DateCoordinationResponse(
        long tripId,
        String startDate,
        String endDate,
        int tripVersion,
        boolean canFinalize,
        List<String> commonDates,
        List<ParticipantAvailability> participants
) {
    public record ParticipantAvailability(
            long userId,
            String nickname,
            String characterKey,
            boolean submitted,
            List<String> dates
    ) {
    }
}
