package com.gayadi.server.friendship.query;

import com.gayadi.server.friendship.model.FriendshipStatus;

import java.time.LocalDateTime;

/** 친구 관계 Repository의 FriendshipQueryResult 조회 결과를 전달합니다. */
public record FriendshipQueryResult(
        long id,
        long requesterId,
        Long blockedBy,
        FriendshipStatus status,
        int version,
        LocalDateTime decidedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        PublicUserQueryResult user
) {
}
