package com.gayadi.server.auth.model;

/** Google ID 토큰에서 확인한 계정 식별 정보입니다. */
public record GoogleIdentity(
        String subject,
        String email,
        boolean emailVerified,
        String name,
        String pictureUrl
) {
}
