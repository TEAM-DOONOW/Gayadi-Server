package com.gayadi.server.friendship.query;

import com.gayadi.server.friendship.model.FriendshipStatus;

/** 친구 관계 Repository의 FriendshipStateQueryResult 조회 결과를 전달합니다. */
public record FriendshipStateQueryResult(
        long id,
        long firstUserId,
        long secondUserId,
        long requesterId,
        Long blockedBy,
        FriendshipStatus status,
        int version
) {
}
