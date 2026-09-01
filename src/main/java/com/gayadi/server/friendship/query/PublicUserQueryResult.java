package com.gayadi.server.friendship.query;

/** 친구 관계 Repository의 PublicUserQueryResult 조회 결과를 전달합니다. */
public record PublicUserQueryResult(
        long id,
        String nickname,
        String introduction,
        String profileImageUrl,
        String characterKey,
        String emoji
) {
}
