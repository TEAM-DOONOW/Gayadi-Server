package com.gayadi.server.auth.query;

import com.gayadi.server.auth.model.UserStatus;

import java.time.LocalDateTime;

/** 인증과 사용자 계정 Repository의 LoginAccountQueryResult 조회 결과를 전달합니다. */
public record LoginAccountQueryResult(
        long userId,
        String email,
        UserStatus status,
        String passwordHash,
        int failedLoginAttempts,
        LocalDateTime loginLockedUntil
) {
}
