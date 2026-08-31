package com.gayadi.server.travel.query;

/** 여행과 참여자 Repository의 ParticipantQueryResult 조회 결과를 전달합니다. */
public record ParticipantQueryResult(
        long participantId,
        long tripId,
        long userId,
        String nickname,
        String characterKey,
        String role,
        String status,
        Long departurePlaceId,
        Long returnPlaceId
) {
}
