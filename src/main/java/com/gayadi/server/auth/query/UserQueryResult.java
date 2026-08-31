package com.gayadi.server.auth.query;

import com.gayadi.server.auth.model.UserStatus;

import java.time.LocalDateTime;

/** 인증과 사용자 계정 Repository의 UserQueryResult 조회 결과를 전달합니다. */
public record UserQueryResult(
        long id,
        String nickname,
        String email,
        String introduction,
        String profileImageUrl,
        UserStatus status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
