package com.gayadi.server.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** MembershipResponse API 응답 데이터를 반환합니다. */
@Schema(name = "MembershipResponse", description = "초대 코드로 여행에 참여한 결과")
public record MembershipResponse(
        Long invitationId,
        TripResponse trip,
        ParticipantResponse participant
) {
}
