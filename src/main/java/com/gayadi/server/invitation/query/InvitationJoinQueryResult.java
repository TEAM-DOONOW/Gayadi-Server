package com.gayadi.server.invitation.query;

import java.time.LocalDateTime;

/** 여행 초대 Repository의 InvitationJoinQueryResult 조회 결과를 전달합니다. */
public record InvitationJoinQueryResult(
        long id,
        long tripId,
        Long inviteeUserId,
        String databaseStatus,
        LocalDateTime expiresAt
) {
}
