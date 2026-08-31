package com.gayadi.server.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** ParticipantResponse API 응답 데이터를 반환합니다. */
@Schema(name = "ParticipantResponse", description = "여행 참여자 정보")
public record ParticipantResponse(
        long id,
        long userId,
        long participantId,
        String nickname,
        String characterKey,
        String role,
        String status,
        Long departurePlaceId,
        Long returnPlaceId,
        Long tripId
) {
}
