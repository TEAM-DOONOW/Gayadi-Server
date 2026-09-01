package com.gayadi.server.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** AuthTokenResponse API 응답 데이터를 반환합니다. */
@Schema(name = "AuthTokenResponse", description = "가입 또는 로그인으로 발급한 인증 정보")
public record AuthTokenResponse(
        @Schema(description = "API 인증에 사용할 JWT", requiredMode = Schema.RequiredMode.REQUIRED)
        String accessToken,

        @Schema(description = "인증 방식", example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
        String tokenType,

        @Schema(description = "토큰 만료까지 남은 초", example = "7200", requiredMode = Schema.RequiredMode.REQUIRED)
        long expiresIn,

        @Schema(description = "로그인한 계정", requiredMode = Schema.RequiredMode.REQUIRED)
        AccountResponse user
) {
}
