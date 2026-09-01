package com.gayadi.server.friendship.query;

import com.gayadi.server.friendship.model.FriendshipStatus;

/** 친구 관계 Repository의 UserSearchQueryResult 조회 결과를 전달합니다. */
public record UserSearchQueryResult(
        PublicUserQueryResult user,
        Long friendshipId,
        FriendshipStatus friendshipStatus,
        Long requesterId,
        Integer friendshipVersion
) {
}
