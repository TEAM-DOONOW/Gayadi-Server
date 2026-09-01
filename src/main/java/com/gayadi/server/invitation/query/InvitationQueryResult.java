package com.gayadi.server.invitation.query;

import com.gayadi.server.invitation.model.InvitationStatus;

import java.time.LocalDateTime;

/** 여행 초대 Repository의 InvitationQueryResult 조회 결과를 전달합니다. */
public record InvitationQueryResult(
        long id,
        long tripId,
        long inviterId,
        String inviterNickname,
        Long inviteeId,
        String inviteeNickname,
        String code,
        InvitationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime acceptedAt,
        LocalDateTime declinedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {
}
